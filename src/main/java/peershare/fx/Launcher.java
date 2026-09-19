package peershare.fx;

/**
 * JavaFX has a JVM-level check that refuses to start an application whose
 * main() is directly on a class extending javafx.application.Application
 * unless it's launched via the module path. Running from a plain classpath
 * jar (java -jar peershare.jar, our maven-shade-plugin output) trips that
 * check and prints a misleading "JavaFX runtime components are missing"
 * error even though the javafx jars ARE on the classpath. The standard,
 * well-known workaround is this: a separate entry-point class that is NOT
 * itself an Application subclass, which just forwards to one. Keep this as
 * the jar's Main-Class / the run scripts' entry point, not PeerShareFxApp.
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        PeerShareFxApp.main(args);
    }
}
