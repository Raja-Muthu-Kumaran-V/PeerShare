package peershare;

import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Client-side counterpart to FileShareServer: asks a remote peer for its file
 * list, or downloads a specific file using the RSA-wrapped-AES handshake and
 * verifies the result against the SHA-256 hash the server sends.
 */
public class FileShareClient {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    public static Map<String, Long> listRemoteFiles(Peer peer) throws IOException {
        Map<String, Long> result = new LinkedHashMap<>();
        try (Socket socket = connect(peer)) {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out.writeUTF("LIST");
            out.flush();
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                result.put(in.readUTF(), in.readLong());
            }
        }
        return result;
    }

    private static Socket connect(Peer peer) throws IOException {
        Socket socket = new Socket();
        socket.connect(new java.net.InetSocketAddress(peer.getHost(), peer.getPort()), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        return socket;
    }

    /**
     * Downloads {@code filename} from {@code peer} into {@code downloadDir}.
     *
     * @param progressCallback optional (nullable) (bytesReceived, totalBytes) callback, invoked
     *                         periodically so the GUI progress dialog can update.
     * @param listener         optional (nullable) fired once with the final outcome, for DB logging.
     */
    public static void downloadFile(Peer peer, String filename, Path downloadDir,
                                     BiConsumer<Long, Long> progressCallback, TransferListener listener) throws Exception {
        String tag = "[" + Thread.currentThread().getName() + "]";

        if (!Validation.isSafeFilename(filename)) {
            return;
        }

        File outFile = null;
        boolean completed = false;
        TransferEvent.Status finalStatus = TransferEvent.Status.FAILED;
        long totalSize = 0;
        String finalHash = null;

        try (Socket socket = connect(peer)) {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            out.writeUTF("GET:" + filename);
            out.flush();

            String status = in.readUTF();
            if (!status.equals("OK")) {
                System.out.println(tag + " Server rejected request: " + status);
                return;
            }

            int pubLen = in.readInt();
            byte[] pubBytes = new byte[pubLen];
            in.readFully(pubBytes);
            PublicKey serverPub = CryptoUtil.decodeRSAPublicKey(pubBytes);

            SecretKey aesKey = CryptoUtil.generateAESKey();
            byte[] wrapped = CryptoUtil.wrapAESKey(aesKey, serverPub);
            out.writeInt(wrapped.length);
            out.write(wrapped);
            out.flush();

            byte[] iv = new byte[CryptoUtil.IV_LENGTH];
            in.readFully(iv);

            String expectedHash = in.readUTF();
            totalSize = in.readLong();

            downloadDir.toFile().mkdirs();
            outFile = uniqueDestination(downloadDir, filename);

            try (CipherInputStream cis = CryptoUtil.decryptStream(in, aesKey, iv);
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buf = new byte[4096];
                long received = 0;
                long lastReportedPct = -1;
                int n;
                while ((n = cis.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                    received += n;
                    if (progressCallback != null) progressCallback.accept(Math.min(received, totalSize), totalSize);
                    long pct = totalSize <= 0 ? 100 : Math.min(100, (received * 100) / totalSize);
                    if (pct - lastReportedPct >= 20 || pct == 100) {
                        ProgressBar.print(tag, Math.min(received, totalSize), totalSize);
                        lastReportedPct = pct;
                    }
                }
            }

            String actualHash = HashUtil.sha256(outFile);
            boolean match = actualHash.equalsIgnoreCase(expectedHash);
            finalHash = actualHash;
            if (!match) System.out.println(tag + " SHA-256 mismatch - discarding file");

            if (!match) {
                System.out.println(tag + " WARNING: downloaded file failed integrity check - discarding it.");
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
                finalStatus = TransferEvent.Status.HASH_MISMATCH;
            } else {
                completed = true;
                finalStatus = TransferEvent.Status.SUCCESS;
            }
        } catch (java.net.SocketTimeoutException e) {
            System.out.println(tag + " Timed out waiting for peer.");
        } catch (java.net.ConnectException e) {
            System.out.println(tag + " Could not connect to " + peer);
        } finally {
            if (!completed && outFile != null && outFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
            }
            if (listener != null) {
                try {
                    listener.onTransferEvent(TransferEvent.now(peer.getName(), peer.getHost() + ":" + peer.getPort(),
                            filename, totalSize, TransferEvent.Direction.RECEIVED, finalStatus, finalHash));
                } catch (Exception ignored) { }
            }
        }
    }

    private static File uniqueDestination(Path downloadDir, String filename) {
        File candidate = downloadDir.resolve(filename).toFile();
        if (!candidate.exists()) return candidate;
        String base = filename, ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot > 0) { base = filename.substring(0, dot); ext = filename.substring(dot); }
        int counter = 1;
        File alt;
        do { alt = downloadDir.resolve(base + " (" + counter + ")" + ext).toFile(); counter++; } while (alt.exists());
        return alt;
    }
}
