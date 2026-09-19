package peershare.db;

import java.time.LocalDateTime;

/**
 * One row in the transfer_history table: a record of a single file transfer
 * (either a file this peer sent, or one it received), including its
 * integrity-check outcome.
 */
public class TransferRecord {

    public enum Direction { SENT, RECEIVED }
    public enum Status { SUCCESS, FAILED, HASH_MISMATCH }

    private Long id; // null until saved and assigned by the database
    private final String peerName;
    private final String peerAddress;
    private final String filename;
    private final long sizeBytes;
    private final Direction direction;
    private final Status status;
    private final String sha256; // nullable - may be unknown for a FAILED transfer
    private final LocalDateTime occurredAt;

    public TransferRecord(Long id, String peerName, String peerAddress, String filename,
                           long sizeBytes, Direction direction, Status status,
                           String sha256, LocalDateTime occurredAt) {
        this.id = id;
        this.peerName = peerName;
        this.peerAddress = peerAddress;
        this.filename = filename;
        this.sizeBytes = sizeBytes;
        this.direction = direction;
        this.status = status;
        this.sha256 = sha256;
        this.occurredAt = occurredAt;
    }

    /** Convenience constructor for a brand-new (not-yet-saved) record. */
    public static TransferRecord newRecord(String peerName, String peerAddress, String filename,
                                            long sizeBytes, Direction direction, Status status, String sha256) {
        return new TransferRecord(null, peerName, peerAddress, filename, sizeBytes,
                direction, status, sha256, LocalDateTime.now());
    }

    public Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    public String getPeerName() { return peerName; }
    public String getPeerAddress() { return peerAddress; }
    public String getFilename() { return filename; }
    public long getSizeBytes() { return sizeBytes; }
    public Direction getDirection() { return direction; }
    public Status getStatus() { return status; }
    public String getSha256() { return sha256; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
