package peershare.db;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises TransferHistoryRepository as an implementation of the generic
 * Repository<TransferRecord, Long> contract, in both of its two modes:
 *  1) in-memory fallback (constructed with a deliberately unreachable DB, via
 *     reflection since DatabaseConfig's constructor is private by design), and
 *  2) against the real MySQL instance this project is configured for, when
 *     one happens to be reachable (skipped gracefully otherwise via
 *     Assumptions so this test suite stays portable to machines without a
 *     local MySQL server).
 */
class TransferHistoryRepositoryTest {

    private static DatabaseConfig unreachableConfig() throws Exception {
        Constructor<DatabaseConfig> ctor = DatabaseConfig.class.getDeclaredConstructor(
                String.class, int.class, String.class, String.class, String.class);
        ctor.setAccessible(true);
        // Port 1 is reserved/unroutable for a MySQL connection in any normal
        // environment, so this reliably forces the fallback path without
        // depending on any specific host actually refusing the connection.
        return ctor.newInstance("127.0.0.1", 1, "doesnotexist", "nobody", "nopass");
    }

    private static TransferRecord sampleRecord() {
        return TransferRecord.newRecord(
                "Peer-Test", "192.0.2.1:12345", "unit-test-file.txt",
                123L, TransferRecord.Direction.SENT, TransferRecord.Status.SUCCESS,
                "deadbeef");
    }

    @Test
    void fallsBackToInMemoryWhenDatabaseUnreachable() throws Exception {
        Repository<TransferRecord, Long> repo = new TransferHistoryRepository(unreachableConfig());

        assertFalse(((TransferHistoryRepository) repo).isDatabaseAvailable(),
                "repository should detect the DB is unreachable at construction time");

        TransferRecord saved = repo.save(sampleRecord());

        assertNotNull(saved.getId(), "even in-memory saves should get an assigned ID");
        assertEquals(1, repo.findAll().size());
        assertEquals("unit-test-file.txt", repo.findAll().get(0).getFilename());
    }

    @Test
    void inMemoryFindByIdAndDeleteByIdRoundTrip() throws Exception {
        Repository<TransferRecord, Long> repo = new TransferHistoryRepository(unreachableConfig());

        TransferRecord saved = repo.save(sampleRecord());
        Long id = saved.getId();

        Optional<TransferRecord> found = repo.findById(id);
        assertTrue(found.isPresent());
        assertEquals("Peer-Test", found.get().getPeerName());

        assertTrue(repo.deleteById(id));
        assertTrue(repo.findById(id).isEmpty());
        assertFalse(repo.deleteById(id), "deleting an already-removed id should return false");
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() throws Exception {
        Repository<TransferRecord, Long> repo = new TransferHistoryRepository(unreachableConfig());
        assertTrue(repo.findById(999_999L).isEmpty());
    }

    @Test
    void savesAndReadsBackFromRealDatabaseWhenAvailable() {
        TransferHistoryRepository repo = new TransferHistoryRepository(DatabaseConfig.load());
        assumeTrue(repo.isDatabaseAvailable(),
                "no reachable MySQL for this config - skipping the live-DB test, in-memory tests above already cover the fallback contract");

        TransferRecord toSave = TransferRecord.newRecord(
                "Peer-RepoTest", "192.0.2.9:9999", "repo-test-" + System.nanoTime() + ".txt",
                42L, TransferRecord.Direction.RECEIVED, TransferRecord.Status.SUCCESS, "cafebabe");

        TransferRecord saved = repo.save(toSave);
        assertNotNull(saved.getId(), "a DB-backed save should get a generated ID");

        List<TransferRecord> all = repo.findAll();
        assertTrue(all.stream().anyMatch(r -> r.getId().equals(saved.getId())),
                "findAll() after a live save should include the row we just inserted");

        assertTrue(repo.deleteById(saved.getId()), "cleanup: remove the row this test inserted");
    }
}
