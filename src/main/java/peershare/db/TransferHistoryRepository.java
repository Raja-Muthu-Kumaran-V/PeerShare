package peershare.db;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores {@link TransferRecord}s in MySQL (via the MariaDB JDBC driver, which
 * is wire-compatible with MySQL). If the database can't be reached at
 * startup - not installed, wrong credentials, server down - PeerShare should
 * not crash or refuse to run: this repository falls back to an in-memory
 * list instead, and the GUI shows a one-time warning so the user knows
 * history won't survive a restart. Every call also has its own try/catch so
 * a transient DB error during normal use degrades to "log a warning and keep
 * the in-memory copy in sync" rather than propagating an exception into the
 * GUI thread.
 */
public class TransferHistoryRepository implements Repository<TransferRecord, Long> {

    private final DatabaseConfig config;
    private volatile boolean dbAvailable;
    private String unavailableReason;

    // Fallback store, also used as a write-through cache when the DB is up
    // (cheap way to serve findAll() without round-tripping for the common case).
    private final List<TransferRecord> memoryStore = new CopyOnWriteArrayList<>();
    private final AtomicLong memoryIdSequence = new AtomicLong(1);
    // Records saved while the DB was unreachable, not yet persisted. Tracked
    // separately so retryConnection() can flush them into the database before
    // reloading - otherwise loadAllFromDatabase()'s memoryStore.clear() would
    // silently discard any history recorded during the outage.
    private final List<TransferRecord> pendingSync = new CopyOnWriteArrayList<>();

    public TransferHistoryRepository(DatabaseConfig config) {
        this.config = config;
        this.dbAvailable = testConnection();
        if (dbAvailable) {
            loadAllFromDatabase();
        }
    }

    public boolean isDatabaseAvailable() { return dbAvailable; }
    public String getUnavailableReason() { return unavailableReason; }

    private boolean testConnection() {
        try (Connection ignored = openConnection()) {
            return true;
        } catch (SQLException e) {
            unavailableReason = e.getMessage();
            return false;
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.user, config.password);
    }

    @Override
    public TransferRecord save(TransferRecord entity) {
        if (dbAvailable) {
            String sql = "INSERT INTO transfer_history " +
                    "(peer_name, peer_address, filename, size_bytes, direction, status, sha256, occurred_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = openConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, entity.getPeerName());
                ps.setString(2, entity.getPeerAddress());
                ps.setString(3, entity.getFilename());
                ps.setLong(4, entity.getSizeBytes());
                ps.setString(5, entity.getDirection().name());
                ps.setString(6, entity.getStatus().name());
                ps.setString(7, entity.getSha256());
                ps.setTimestamp(8, Timestamp.valueOf(entity.getOccurredAt()));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) entity.setId(keys.getLong(1));
                }
                memoryStore.add(0, entity);
                return entity;
            } catch (SQLException e) {
                System.err.println("[TransferHistoryRepository] insert failed, falling back to memory for this record: " + e.getMessage());
                dbAvailable = false; // stop hammering a possibly-dead connection; GUI can offer a retry
                unavailableReason = e.getMessage();
                entity.setId(memoryIdSequence.getAndIncrement());
                memoryStore.add(0, entity);
                pendingSync.add(entity);
                return entity;
            }
        }
        entity.setId(memoryIdSequence.getAndIncrement());
        memoryStore.add(0, entity);
        pendingSync.add(entity);
        return entity;
    }

    @Override
    public List<TransferRecord> findAll() {
        if (dbAvailable) {
            loadAllFromDatabase();
        }
        return new ArrayList<>(memoryStore);
    }

    @Override
    public Optional<TransferRecord> findById(Long id) {
        return memoryStore.stream().filter(r -> Objects.equals(r.getId(), id)).findFirst();
    }

    @Override
    public boolean deleteById(Long id) {
        if (dbAvailable) {
            try (Connection conn = openConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM transfer_history WHERE id = ?")) {
                ps.setLong(1, id);
                int rows = ps.executeUpdate();
                memoryStore.removeIf(r -> Objects.equals(r.getId(), id));
                return rows > 0;
            } catch (SQLException e) {
                System.err.println("[TransferHistoryRepository] delete failed: " + e.getMessage());
                return false;
            }
        }
        return memoryStore.removeIf(r -> Objects.equals(r.getId(), id));
    }

    /** Lets the GUI offer a manual "reconnect to database" action instead of only checking once at startup. */
    public boolean retryConnection() {
        dbAvailable = testConnection();
        if (dbAvailable) {
            syncPendingToDatabase();
            loadAllFromDatabase();
        }
        return dbAvailable;
    }

    /** Persists any records that were only ever saved to memory (recorded while
     *  the DB was unreachable) before a reload would otherwise overwrite them. */
    private void syncPendingToDatabase() {
        if (pendingSync.isEmpty()) return;
        String sql = "INSERT INTO transfer_history " +
                "(peer_name, peer_address, filename, size_bytes, direction, status, sha256, occurred_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        List<TransferRecord> toSync = new ArrayList<>(pendingSync);
        try (Connection conn = openConnection()) {
            for (TransferRecord entity : toSync) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, entity.getPeerName());
                    ps.setString(2, entity.getPeerAddress());
                    ps.setString(3, entity.getFilename());
                    ps.setLong(4, entity.getSizeBytes());
                    ps.setString(5, entity.getDirection().name());
                    ps.setString(6, entity.getStatus().name());
                    ps.setString(7, entity.getSha256());
                    ps.setTimestamp(8, Timestamp.valueOf(entity.getOccurredAt()));
                    ps.executeUpdate();
                    pendingSync.remove(entity);
                } catch (SQLException e) {
                    System.err.println("[TransferHistoryRepository] could not sync a pending record, will retry next reconnect: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("[TransferHistoryRepository] pending-sync connection failed: " + e.getMessage());
        }
    }

    private void loadAllFromDatabase() {
        String sql = "SELECT id, peer_name, peer_address, filename, size_bytes, direction, status, sha256, occurred_at " +
                "FROM transfer_history ORDER BY occurred_at DESC";
        try (Connection conn = openConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            List<TransferRecord> fresh = new ArrayList<>();
            while (rs.next()) {
                fresh.add(new TransferRecord(
                        rs.getLong("id"),
                        rs.getString("peer_name"),
                        rs.getString("peer_address"),
                        rs.getString("filename"),
                        rs.getLong("size_bytes"),
                        TransferRecord.Direction.valueOf(rs.getString("direction")),
                        TransferRecord.Status.valueOf(rs.getString("status")),
                        rs.getString("sha256"),
                        rs.getTimestamp("occurred_at").toLocalDateTime()
                ));
            }
            memoryStore.clear();
            memoryStore.addAll(fresh);
        } catch (SQLException e) {
            System.err.println("[TransferHistoryRepository] load failed, keeping cached copy: " + e.getMessage());
            dbAvailable = false;
            unavailableReason = e.getMessage();
        }
    }
}
