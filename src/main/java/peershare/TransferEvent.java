package peershare;

import java.time.LocalDateTime;

/**
 * Immutable record of one completed (or failed) transfer, fired by
 * FileShareServer/FileShareClient via a {@link TransferListener}. Kept in
 * the core peershare package (not peershare.db) so the networking layer has
 * no dependency on the database layer - whoever wires things together (the
 * GUI's Main) is free to persist this, log it, ignore it, or all three.
 */
public record TransferEvent(
        String peerName,
        String peerAddress,
        String filename,
        long sizeBytes,
        Direction direction,
        Status status,
        String sha256,
        LocalDateTime occurredAt
) {
    public enum Direction { SENT, RECEIVED }
    public enum Status { SUCCESS, FAILED, HASH_MISMATCH }

    public static TransferEvent now(String peerName, String peerAddress, String filename,
                                     long sizeBytes, Direction direction, Status status, String sha256) {
        return new TransferEvent(peerName, peerAddress, filename, sizeBytes, direction, status, sha256, LocalDateTime.now());
    }
}
