package peershare;

/** Small set of defensive validation helpers used throughout PeerShare. */
public final class Validation {
    private Validation() {}

    public static boolean isSafeFilename(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.contains("/") || trimmed.contains("\\")) return false;
        if (trimmed.equals(".") || trimmed.equals("..")) return false;
        if (trimmed.startsWith("~")) return false;
        return true;
    }

    public static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    public static boolean isValidPeerName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return !trimmed.isEmpty() && trimmed.length() <= 40 && !trimmed.contains("|");
    }
}
