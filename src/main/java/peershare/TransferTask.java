package peershare;

import java.nio.file.Path;
import java.util.function.BiConsumer;

/** Runnable wrapper around FileShareClient.downloadFile so a download can run on its own thread. */
public class TransferTask implements Runnable {

    private final Peer peer;
    private final String filename;
    private final Path downloadDir;
    private final BiConsumer<Long, Long> progressCallback; // nullable
    private final TransferListener listener; // nullable

    public TransferTask(Peer peer, String filename, Path downloadDir,
                         BiConsumer<Long, Long> progressCallback, TransferListener listener) {
        this.peer = peer;
        this.filename = filename;
        this.downloadDir = downloadDir;
        this.progressCallback = progressCallback;
        this.listener = listener;
    }

    @Override
    public void run() {
        String tag = "[" + Thread.currentThread().getName() + "]";
        try {
            FileShareClient.downloadFile(peer, filename, downloadDir, progressCallback, listener);
        } catch (Exception e) {
            System.out.println(tag + " Transfer failed for " + filename + ": " + e.getMessage());
        }
    }
}
