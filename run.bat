@echo off
REM Runs PeerShare (JavaFX GUI edition). Maven is required now - unlike the old
REM JavaFX platform-native jars are resolved by Maven
REM (see the OS profiles in pom.xml), so there's no javac-only fallback anymore.
cd /d "%~dp0"

where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Maven is required to run the JavaFX build of PeerShare ^(it resolves the
    echo platform-specific JavaFX runtime jars^) but 'mvn' was not found on your PATH.
    echo Install Maven, then re-run this script.
    exit /b 1
)

set JAR=target\peershare.jar

if exist "%JAR%" (
    echo Launching %JAR% ...
    java -jar "%JAR%"
    goto :eof
)

echo No built jar found - building with Maven ^(requires internet access to Maven Central the first time^) ...
call mvn -q package
if exist "%JAR%" (
    echo Launching %JAR% ...
    java -jar "%JAR%"
    goto :eof
)

echo Build failed - trying 'mvn javafx:run' instead ^(runs without packaging a jar^) ...
call mvn -q javafx:run
