package peershare;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SharedFileRegistry {
    private final Path sharedDir;
    private final ConcurrentHashMap<String, Path> folderFiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Path> pickedFiles = new ConcurrentHashMap<>();

    public SharedFileRegistry(String sharedDirPath) {
        this.sharedDir = Paths.get(sharedDirPath);
        try {
            Files.createDirectories(sharedDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create shared directory: " + sharedDirPath, e);
        }
        refresh();
    }

    public void refresh() {
        folderFiles.clear();
        File[] list = sharedDir.toFile().listFiles(File::isFile);
        if (list != null) for (File f : list) folderFiles.put(f.getName(), f.toPath());
    }

    public synchronized String addPickedFile(Path absolutePath) {
        String key = uniqueKeyFor(absolutePath);
        pickedFiles.put(key, absolutePath);
        return key;
    }

    private String uniqueKeyFor(Path absolutePath) {
        String baseName = absolutePath.getFileName().toString();
        if (!isTaken(baseName)) return baseName;
        Path existing = resolveInternal(baseName);
        if (existing != null && pathsEqual(existing, absolutePath)) return baseName;

        String base = baseName, ext = "";
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) { base = baseName.substring(0, dot); ext = baseName.substring(dot); }
        int counter = 1;
        String candidate;
        do { candidate = base + " (" + counter + ")" + ext; counter++; } while (isTaken(candidate));
        return candidate;
    }

    private boolean isTaken(String key) { return folderFiles.containsKey(key) || pickedFiles.containsKey(key); }
    private Path resolveInternal(String key) { Path p = pickedFiles.get(key); return p != null ? p : folderFiles.get(key); }

    private static boolean pathsEqual(Path a, Path b) {
        try { return Files.isSameFile(a, b); }
        catch (IOException e) { return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize()); }
    }

    public synchronized boolean removePickedFile(String name) { return pickedFiles.remove(name) != null; }

    public Optional<Path> getFile(String name) {
        refresh();
        Path p = pickedFiles.get(name);
        if (p == null) p = folderFiles.get(name);
        return Optional.ofNullable(p);
    }

    public List<String> listFileNames() {
        refresh();
        Set<String> names = new TreeSet<>();
        names.addAll(folderFiles.keySet());
        names.addAll(pickedFiles.keySet());
        return new ArrayList<>(names);
    }

    public long sizeOf(String name) {
        Path p = pickedFiles.get(name);
        if (p == null) p = folderFiles.get(name);
        return p == null ? -1 : p.toFile().length();
    }

    public boolean isPicked(String name) { return pickedFiles.containsKey(name); }

    public Path getSourcePath(String name) {
        Path p = pickedFiles.get(name);
        return p != null ? p : folderFiles.get(name);
    }

    public Path getSharedDir() { return sharedDir; }
}
