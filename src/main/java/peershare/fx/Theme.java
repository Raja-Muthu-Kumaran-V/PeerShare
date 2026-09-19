package peershare.fx;

import javafx.scene.Scene;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Applies the light/dark CSS theme to a JavaFX {@link Scene} and
 * remembers the choice in the same {@code ~/.peershare/prefs.properties} file
 * the setup screen already uses for peer name/port/folders, under the key
 * "theme". Every open Scene is tracked here so the toggle in the main window
 * can re-skin dialogs too, not just the window it was clicked in.
 */
public final class Theme {
    private Theme() {}

    private static final File PREFS_DIR = new File(System.getProperty("user.home"), ".peershare");
    private static final File PREFS_FILE = new File(PREFS_DIR, "prefs.properties");

    private static final String COMMON_CSS = "common.css";
    private static final String LIGHT_CSS = "light.css";
    private static final String DARK_CSS = "dark.css";

    private static boolean dark = loadPref();
    private static final List<Scene> trackedScenes = new ArrayList<>();

    public static boolean isDark() { return dark; }

    /** Registers a scene so future toggle() calls re-skin it too, and applies the current theme now. */
    public static void track(Scene scene) {
        trackedScenes.add(scene);
        apply(scene);
    }

    public static void toggle() {
        dark = !dark;
        savePref(dark);
        for (Scene s : trackedScenes) apply(s);
    }

    private static void apply(Scene scene) {
        scene.getStylesheets().setAll(
                Theme.class.getResource(dark ? DARK_CSS : LIGHT_CSS).toExternalForm(),
                Theme.class.getResource(COMMON_CSS).toExternalForm()
        );
    }

    private static boolean loadPref() {
        if (!PREFS_FILE.isFile()) return false; // default: light
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(PREFS_FILE)) {
            p.load(in);
        } catch (IOException ignored) {
            return false;
        }
        return "dark".equals(p.getProperty("theme"));
    }

    private static void savePref(boolean darkValue) {
        try {
            if (!PREFS_DIR.isDirectory()) PREFS_DIR.mkdirs();
            Properties p = new Properties();
            if (PREFS_FILE.isFile()) {
                try (FileInputStream in = new FileInputStream(PREFS_FILE)) {
                    p.load(in);
                } catch (IOException ignored) { }
            }
            p.setProperty("theme", darkValue ? "dark" : "light");
            try (FileOutputStream out = new FileOutputStream(PREFS_FILE)) {
                p.store(out, "PeerShare preferences");
            }
        } catch (IOException ignored) {
            // Non-fatal: worst case the theme choice doesn't survive to next launch.
        }
    }
}
