@echo off
REM One-time setup for PeerShare on Windows:
REM   1. Adds Windows Defender Firewall rules for the UDP discovery port
REM      (9876) and a TCP port for file transfers.
REM   2. Pre-builds target\peershare.jar with Maven if it's available, so
REM      the first launch via run.bat is instant instead of building on
REM      the spot.
REM
REM Usage:
REM   setup-windows.bat [tcp-port]
REM
REM If [tcp-port] is omitted, the whole 50000-55000 range is opened,
REM matching the random default port range StartupDialog suggests on first
REM launch. If you pick a specific port in the app, re-run this script with
REM that port for a tighter firewall rule, e.g.:
REM   setup-windows.bat 51234
REM
REM MUST be run as Administrator (right-click -^> "Run as administrator"),
REM or the netsh commands below will fail.

setlocal
cd /d "%~dp0"

set DISCOVERY_PORT=9876
set TCP_PORT=%1

echo === PeerShare Windows setup ===

net session >nul 2>&1
if not %ERRORLEVEL%==0 (
    echo NOTE: this does not look like an elevated ^(Administrator^) prompt.
    echo Right-click setup-windows.bat and choose "Run as administrator", then re-run.
    echo Continuing anyway - the netsh commands below may simply fail.
    echo.
)

echo Adding firewall rule: PeerShare Discovery ^(UDP %DISCOVERY_PORT%^) ...
netsh advfirewall firewall add rule name="PeerShare Discovery (UDP %DISCOVERY_PORT%)" dir=in action=allow protocol=UDP localport=%DISCOVERY_PORT%

if "%TCP_PORT%"=="" (
    echo Adding firewall rule: PeerShare Transfer ^(TCP 50000-55000, default range^) ...
    netsh advfirewall firewall add rule name="PeerShare Transfer (TCP 50000-55000)" dir=in action=allow protocol=TCP localport=50000-55000
) else (
    echo Adding firewall rule: PeerShare Transfer ^(TCP %TCP_PORT%^) ...
    netsh advfirewall firewall add rule name="PeerShare Transfer (TCP %TCP_PORT%)" dir=in action=allow protocol=TCP localport=%TCP_PORT%
)

echo.
where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
    echo Pre-building target\peershare.jar with Maven...
    call mvn -q package
    if exist target\peershare.jar (
        echo Build OK - run.bat will now launch instantly.
    ) else (
        echo Build failed - run.bat will fall back to the javac path at launch.
    )
) else (
    echo Maven not found - skipping pre-build. run.bat will compile with javac on first launch.
)

echo.
echo Setup complete. Run PeerShare with: run.bat
endlocal
