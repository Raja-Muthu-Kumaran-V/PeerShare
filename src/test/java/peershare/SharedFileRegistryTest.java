package peershare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SharedFileRegistryTest {

    @Test
    void createsSharedDirectoryIfMissing(@TempDir Path tempDir) {
        Path sharedDir = tempDir.resolve("does-not-exist-yet");
        assertFalse(Files.exists(sharedDir));

        new SharedFileRegistry(sharedDir.toString());

        assertTrue(Files.isDirectory(sharedDir));
    }

    @Test
    void listsFilesAlreadyInSharedFolder(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "one");
        Files.writeString(tempDir.resolve("b.txt"), "two");

        SharedFileRegistry registry = new SharedFileRegistry(tempDir.toString());

        List<String> names = registry.listFileNames();
        assertEquals(List.of("a.txt", "b.txt"), names); // TreeSet -> sorted
    }

    @Test
    void pickedFileFromOutsideSharedFolderIsListedAndResolvable(@TempDir Path tempDir) throws IOException {
        Path sharedDir = tempDir.resolve("shared");
        Files.createDirectories(sharedDir);
        Path elsewhere = tempDir.resolve("elsewhere");
        Files.createDirectories(elsewhere);
        Path pickedFile = elsewhere.resolve("picked.txt");
        Files.writeString(pickedFile, "picked content");

        SharedFileRegistry registry = new SharedFileRegistry(sharedDir.toString());
        String key = registry.addPickedFile(pickedFile);

        assertEquals("picked.txt", key);
        assertTrue(registry.listFileNames().contains("picked.txt"));
        assertTrue(registry.isPicked("picked.txt"));

        Optional<Path> resolved = registry.getFile("picked.txt");
        assertTrue(resolved.isPresent());
        assertEquals(pickedFile.toAbsolutePath().normalize(), resolved.get().toAbsolutePath().normalize());
    }

    @Test
    void addingPickedFileWithDuplicateNameGetsSuffixed(@TempDir Path tempDir) throws IOException {
        Path sharedDir = tempDir.resolve("shared");
        Files.createDirectories(sharedDir);
        Files.writeString(sharedDir.resolve("report.txt"), "already in shared folder");

        Path otherDir = tempDir.resolve("other");
        Files.createDirectories(otherDir);
        Path duplicateNamedFile = otherDir.resolve("report.txt");
        Files.writeString(duplicateNamedFile, "a different file, same name");

        SharedFileRegistry registry = new SharedFileRegistry(sharedDir.toString());
        String key = registry.addPickedFile(duplicateNamedFile);

        assertEquals("report (1).txt", key);
        assertTrue(registry.listFileNames().containsAll(List.of("report.txt", "report (1).txt")));
    }

    @Test
    void reAddingTheSameExactPickedFileReturnsSameKey(@TempDir Path tempDir) throws IOException {
        Path sharedDir = tempDir.resolve("shared");
        Files.createDirectories(sharedDir);
        Path otherDir = tempDir.resolve("other");
        Files.createDirectories(otherDir);
        Path file = otherDir.resolve("same.txt");
        Files.writeString(file, "content");

        SharedFileRegistry registry = new SharedFileRegistry(sharedDir.toString());
        String firstKey = registry.addPickedFile(file);
        String secondKey = registry.addPickedFile(file);

        assertEquals(firstKey, secondKey, "re-sharing the identical path shouldn't create a (1) duplicate entry");
    }

    @Test
    void removePickedFileTakesItOutOfTheListing(@TempDir Path tempDir) throws IOException {
        Path sharedDir = tempDir.resolve("shared");
        Files.createDirectories(sharedDir);
        Path otherDir = tempDir.resolve("other");
        Files.createDirectories(otherDir);
        Path file = otherDir.resolve("temp.txt");
        Files.writeString(file, "content");

        SharedFileRegistry registry = new SharedFileRegistry(sharedDir.toString());
        registry.addPickedFile(file);
        assertTrue(registry.listFileNames().contains("temp.txt"));

        boolean removed = registry.removePickedFile("temp.txt");

        assertTrue(removed);
        assertFalse(registry.listFileNames().contains("temp.txt"));
        assertFalse(registry.removePickedFile("temp.txt"), "removing again should report nothing was removed");
    }

    @Test
    void sizeOfReturnsMinusOneForUnknownFile(@TempDir Path tempDir) {
        SharedFileRegistry registry = new SharedFileRegistry(tempDir.toString());
        assertEquals(-1, registry.sizeOf("nonexistent.txt"));
    }

    @Test
    void sizeOfReturnsActualByteCount(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("sized.txt"), "12345"); // 5 bytes

        SharedFileRegistry registry = new SharedFileRegistry(tempDir.toString());
        assertEquals(5, registry.sizeOf("sized.txt"));
    }

    @Test
    void refreshPicksUpFilesAddedToTheFolderAfterConstruction(@TempDir Path tempDir) throws IOException {
        SharedFileRegistry registry = new SharedFileRegistry(tempDir.toString());
        assertTrue(registry.listFileNames().isEmpty());

        Files.writeString(tempDir.resolve("new-file.txt"), "content");

        // listFileNames() calls refresh() internally, so no explicit refresh() call needed.
        assertTrue(registry.listFileNames().contains("new-file.txt"));
    }
}
