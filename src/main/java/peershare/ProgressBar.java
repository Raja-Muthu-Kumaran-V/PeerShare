package peershare;

public final class ProgressBar {
    private ProgressBar() {}

    public static synchronized void print(String threadTag, long transferred, long total) {
        int width = 10;
        double ratio = total <= 0 ? 1.0 : Math.min(1.0, (double) transferred / (double) total);
        int filled = (int) Math.round(ratio * width);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < width; i++) bar.append(i < filled ? '#' : '-');
        System.out.printf("%s Transfer: %s %3d%% | %s/%s%n",
                threadTag, bar, (int) Math.round(ratio * 100), formatBytes(transferred), formatBytes(total));
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1fKB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1fMB", mb);
        return String.format("%.1fGB", mb / 1024.0);
    }
}
