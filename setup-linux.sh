#!/usr/bin/env bash
# One-time setup for PeerShare on Linux:
#   1. Opens the UDP discovery port (9876) and a TCP port for file transfers
#      in ufw or firewalld, whichever is active.
#   2. Pre-builds target/peershare.jar with Maven if it's available, so the
#      first launch via run.sh is instant instead of building on the spot.
#
# Usage:
#   ./setup-linux.sh [tcp-port]
#
# If [tcp-port] is omitted, the whole 50000-55000 range is opened, matching
# the random default port range StartupDialog suggests on first launch. If
# you pick a specific port in the app, re-run this script with that port
# for a tighter firewall rule, e.g.:
#   ./setup-linux.sh 51234
#
# Must be run with sudo/root for the firewall rules to take effect.

set -u
cd "$(dirname "$0")"

DISCOVERY_PORT=9876
TCP_PORT="${1:-}"

echo "=== PeerShare Linux setup ==="

if [ "$(id -u)" -ne 0 ]; then
    echo "NOTE: not running as root - firewall rule commands below may fail."
    echo "Re-run with: sudo ./setup-linux.sh ${TCP_PORT}"
    echo
fi

open_ufw() {
    echo "Detected ufw - opening ports..."
    ufw allow "${DISCOVERY_PORT}/udp" comment "PeerShare discovery"
    if [ -n "$TCP_PORT" ]; then
        ufw allow "${TCP_PORT}/tcp" comment "PeerShare file transfer"
    else
        ufw allow 50000:55000/tcp comment "PeerShare file transfer (default port range)"
    fi
    ufw reload
}

open_firewalld() {
    echo "Detected firewalld - opening ports..."
    firewall-cmd --permanent --add-port="${DISCOVERY_PORT}/udp"
    if [ -n "$TCP_PORT" ]; then
        firewall-cmd --permanent --add-port="${TCP_PORT}/tcp"
    else
        firewall-cmd --permanent --add-port=50000-55000/tcp
    fi
    firewall-cmd --reload
}

if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
    open_ufw
elif command -v firewall-cmd >/dev/null 2>&1 && systemctl is-active --quiet firewalld 2>/dev/null; then
    open_firewalld
else
    echo "No active ufw or firewalld detected - skipping firewall rules."
    echo "If peers still can't reach each other, check whatever firewall you do have"
    echo "and allow UDP ${DISCOVERY_PORT} plus your chosen TCP port manually."
fi

echo
if command -v mvn >/dev/null 2>&1; then
    echo "Pre-building target/peershare.jar with Maven..."
    mvn -q package && echo "Build OK - run.sh will now launch instantly." \
        || echo "Build failed - run.sh will fall back to the javac path at launch."
else
    echo "Maven not found - skipping pre-build. run.sh will compile with javac on first launch."
fi

echo
echo "Setup complete. Run PeerShare with: ./run.sh"
