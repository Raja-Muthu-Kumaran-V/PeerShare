# PeerShare

Peer-to-peer file sharing for a local network. Built with Java and JavaFX.

Peers discover each other using UDP broadcast, transfer files over TCP with RSA + AES encryption, verify integrity with SHA-256, and optionally log transfers to MySQL.

## Features

- JavaFX desktop GUI (light / dark theme)
- Automatic peer discovery on the same LAN
- Manual peer add (useful when broadcast is blocked)
- Secure file transfer (RSA key exchange + AES)
- SHA-256 integrity check after every transfer
- Transfer history (MySQL or in-memory fallback)
- Drag-and-drop file sharing

## Requirements

- JDK 21
- Maven
- MySQL / MariaDB (optional)

## How to run

```bash
# Linux
./run.sh

# Windows
run.bat
