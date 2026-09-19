package peershare;

import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Listens for incoming TCP connections from other peers and serves LIST /
 * GET:&lt;filename&gt; requests. Every accepted connection is handled on its own
 * thread from a cached pool, so multiple peers (or repeated requests from
 * the same peer) are served concurrently.
 */
public class FileShareServer implements Runnable {

    private static final int MAX_CONCURRENT_CONNECTIONS = 50;
    private static final int CLIENT_SOCKET_TIMEOUT_MS = 20_000;

    private final int port;
    private final SharedFileRegistry registry;
    private final KeyPair rsaKeyPair;
    private volatile TransferListener listener; // nullable; settable after construction
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final java.util.concurrent.Semaphore connectionSlots = new java.util.concurrent.Semaphore(MAX_CONCURRENT_CONNECTIONS);
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    // Resolves a connecting client's raw IP address to a human-readable peer name
    // (e.g. via the discovery registry). Falls back to the IP itself when the
    // peer isn't known (e.g. it hasn't broadcast yet, or came from outside LAN
    // discovery). Nullable-safe: defaults to identity if not supplied.
    private final java.util.function.Function<String, String> peerNameResolver;

    public FileShareServer(int port, SharedFileRegistry registry, KeyPair rsaKeyPair, TransferListener listener) {
        this(port, registry, rsaKeyPair, listener, java.util.function.Function.identity());
    }

    public FileShareServer(int port, SharedFileRegistry registry, KeyPair rsaKeyPair, TransferListener listener,
                            java.util.function.Function<String, String> peerNameResolver) {
        this.port = port;
        this.registry = registry;
        this.rsaKeyPair = rsaKeyPair;
        this.listener = listener;
        this.peerNameResolver = peerNameResolver != null ? peerNameResolver : java.util.function.Function.identity();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
    }

    public void setListener(TransferListener listener) {
        this.listener = listener;
    }

    @Override
    public void run() {
        if (serverSocket == null) throw new IllegalStateException("start() must be called (and succeed) before run()");
        while (running) {
            try {
                Socket client = serverSocket.accept();
                if (!connectionSlots.tryAcquire()) { closeQuietly(client); continue; }
                pool.submit(() -> {
                    try { handleClient(client); }
                    finally { connectionSlots.release(); }
                });
            } catch (IOException e) {
                if (running) System.out.println("[Server] Accept error: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) pool.shutdownNow();
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }

    private static void closeQuietly(Socket s) { try { s.close(); } catch (IOException ignored) {} }

    private void handleClient(Socket socket) {
        String tag = "[" + Thread.currentThread().getName() + "]";
        String remoteAddr = socket.getInetAddress() != null ? socket.getInetAddress().getHostAddress() : "unknown";
        try (Socket s = socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()))) {

            s.setSoTimeout(CLIENT_SOCKET_TIMEOUT_MS);
            String command = in.readUTF();

            if (command.equals("LIST")) {
                serveList(out);
            } else if (command.startsWith("GET:")) {
                String filename = command.substring(4);
                if (!Validation.isSafeFilename(filename)) {
                    out.writeUTF("ERROR:INVALID_NAME");
                    out.flush();
                    return;
                }
                serveFile(tag, remoteAddr, filename, in, out);
            } else {
                out.writeUTF("ERROR:UNKNOWN_COMMAND");
                out.flush();
            }
        } catch (java.net.SocketTimeoutException e) {
            System.out.println(tag + " Timed out waiting for peer");
        } catch (EOFException e) {
            // peer disconnected mid-protocol
        } catch (Exception e) {
            System.out.println(tag + " Transfer failed: " + e.getMessage());
        }
    }

    private void serveList(DataOutputStream out) throws IOException {
        registry.refresh();
        var names = registry.listFileNames();
        out.writeInt(names.size());
        for (String name : names) {
            out.writeUTF(name);
            out.writeLong(registry.sizeOf(name));
        }
        out.flush();
    }

    private void serveFile(String tag, String remoteAddr, String filename, DataInputStream in, DataOutputStream out) throws Exception {
        var fileOpt = registry.getFile(filename);
        if (fileOpt.isEmpty()) {
            out.writeUTF("ERROR:NOT_FOUND");
            out.flush();
            return;
        }
        Path path = fileOpt.get();
        File file = path.toFile();

        if (!file.isFile() || !file.canRead()) {
            out.writeUTF("ERROR:NOT_FOUND");
            out.flush();
            return;
        }

        long size = file.length();
        try {
            out.writeUTF("OK");

            byte[] pubKeyBytes = rsaKeyPair.getPublic().getEncoded();
            out.writeInt(pubKeyBytes.length);
            out.write(pubKeyBytes);
            out.flush();

            int wrappedLen = in.readInt();
            byte[] wrapped = new byte[wrappedLen];
            in.readFully(wrapped);
            SecretKey aesKey = CryptoUtil.unwrapAESKey(wrapped, rsaKeyPair.getPrivate());

            byte[] iv = CryptoUtil.generateIV();
            out.write(iv);

            String hash = HashUtil.sha256(file);
            out.writeUTF(hash);
            out.writeLong(size);
            out.flush();

            try (FileInputStream fis = new FileInputStream(file);
                 CipherOutputStream cos = CryptoUtil.encryptStream(out, aesKey, iv)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = fis.read(buf)) != -1) cos.write(buf, 0, n);
                cos.flush();
            }
            out.flush();

            fireEvent(remoteAddr, filename, size, TransferEvent.Direction.SENT, TransferEvent.Status.SUCCESS, hash);
        } catch (Exception e) {
            fireEvent(remoteAddr, filename, size, TransferEvent.Direction.SENT, TransferEvent.Status.FAILED, null);
            throw e;
        }
    }

    private void fireEvent(String remoteAddr, String filename, long size,
                            TransferEvent.Direction direction, TransferEvent.Status status, String sha256) {
        if (listener != null) {
            try {
                String peerName = peerNameResolver.apply(remoteAddr);
                listener.onTransferEvent(TransferEvent.now(peerName, remoteAddr, filename, size, direction, status, sha256));
            } catch (Exception ignored) {
                // a broken listener must never take down a file transfer
            }
        }
    }
}
