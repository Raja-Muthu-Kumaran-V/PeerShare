#!/usr/bin/env bash
# Runs PeerShare (JavaFX GUI edition). Maven is required now - unlike the old
# JavaFX platform-native jars are resolved by Maven
# (see the OS profiles in pom.xml), so there's no javac-only fallback anymore.
cd "$(dirname "$0")"

if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven is required to run the JavaFX build of PeerShare (it resolves the"
    echo "platform-specific JavaFX runtime jars) but 'mvn' was not found on your PATH."
    echo "Install Maven, then re-run this script."
    exit 1
fi

JAR="target/peershare.jar"

if [ -f "$JAR" ]; then
    echo "Launching $JAR ..."
    java -jar "$JAR"
    exit $?
fi

echo "No built jar found - building with Maven (requires internet access to Maven Central the first time) ..."
mvn -q package
if [ -f "$JAR" ]; then
    echo "Launching $JAR ..."
    java -jar "$JAR"
    exit $?
fi

echo "Build failed - trying 'mvn javafx:run' instead (runs without packaging a jar) ..."
mvn -q javafx:run
