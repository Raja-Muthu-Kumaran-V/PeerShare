package peershare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilTest {

    // Verified independently via `printf 'hello world' | sha256sum`.
    private static final String HELLO_WORLD_SHA256 =
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";

    // Verified independently via `printf '' | sha256sum` - the well-known
    // SHA-256 digest of zero bytes.
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void sha256OfKnownContentMatchesKnownDigest(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("hello.txt").toFile();
        try (FileWriter w = new FileWriter(file)) {
            w.write("hello world");
        }

        assertEquals(HELLO_WORLD_SHA256, HashUtil.sha256(file));
    }

    @Test
    void sha256IsDeterministicForSameContent(@TempDir Path tempDir) throws Exception {
        File a = tempDir.resolve("a.txt").toFile();
        File b = tempDir.resolve("b.txt").toFile();
        try (FileWriter w = new FileWriter(a)) { w.write("PeerShare test content 12345"); }
        try (FileWriter w = new FileWriter(b)) { w.write("PeerShare test content 12345"); }

        assertEquals(HashUtil.sha256(a), HashUtil.sha256(b));
    }

    @Test
    void sha256DiffersForDifferentContent(@TempDir Path tempDir) throws Exception {
        File a = tempDir.resolve("a.txt").toFile();
        File b = tempDir.resolve("b.txt").toFile();
        try (FileWriter w = new FileWriter(a)) { w.write("content A"); }
        try (FileWriter w = new FileWriter(b)) { w.write("content B"); }

        assertNotEquals(HashUtil.sha256(a), HashUtil.sha256(b));
    }

    @Test
    void sha256OutputIsLowercase64CharHex(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("data.txt").toFile();
        try (FileWriter w = new FileWriter(file)) { w.write("some data"); }

        String digest = HashUtil.sha256(file);

        assertEquals(64, digest.length());
        assertTrue(digest.matches("[0-9a-f]{64}"), "digest should be lowercase hex: " + digest);
    }

    @Test
    void sha256OfEmptyFileIsWellKnownEmptyDigest(@TempDir Path tempDir) throws Exception {
        File empty = tempDir.resolve("empty.txt").toFile();
        assertTrue(empty.createNewFile());

        assertEquals(EMPTY_SHA256, HashUtil.sha256(empty));
    }

    @Test
    void bytesToHexHandlesEmptyArray() {
        assertEquals("", HashUtil.bytesToHex(new byte[0]));
    }

    @Test
    void bytesToHexProducesLowercasePairs() {
        byte[] bytes = { (byte) 0x00, (byte) 0xFF, (byte) 0x1A };
        assertEquals("00ff1a", HashUtil.bytesToHex(bytes));
    }
}
