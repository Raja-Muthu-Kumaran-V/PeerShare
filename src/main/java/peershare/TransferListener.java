package peershare;

/** Callback fired whenever a transfer finishes (successfully or not). */
@FunctionalInterface
public interface TransferListener {
    void onTransferEvent(TransferEvent event);
}
