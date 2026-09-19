# PeerShare

A peer-to-peer file sharing desktop application for a LAN, built in Java
with a JavaFX GUI. Built for CS5304 (Java Programming).

Peers find each other with a UDP broadcast, transfer files over TCP with
an AES session key exchanged via RSA, verify every transfer with SHA-256,
and log every send/receive to a MySQL transfer-history table.

> **GUI note:** This project uses a JavaFX GUI (with toggleable light/dark theme).
> Built with a JavaFX GUI (toggleable light/dark theme).

## Features

- **Real desktop GUI** (JavaFX, with a toggleable light/dark theme) - no
  command line required to use the app.
- **Peer discovery** over UDP broadcast; peers appear/disappear live as they
  come online and go offline.
- **Manual peer add** - add a peer directly by IP/port when broadcast
  discovery is blocked (campus Wi-Fi, VPNs, hotspots); a network banner and
  a "Test My Network" dialog help diagnose why discovery isn't finding
  anyone. See "Peer discovery across different networks" below.
- **File transfer** over TCP, with a live progress dialog per transfer.
- **Native file chooser** (JavaFX `FileChooser`/`DirectoryChooser`) - share
  any file from anywhere on disk, not just a fixed folder. Files/folders
  can also be **dragged and dropped** onto the "My Shared Files" table.
- **Security on every transfer**: RSA-2048 key exchange, AES-128/CBC for the
  file bytes, SHA-256 integrity verification after every transfer.
- **MySQL-backed transfer history** - every send and receive is logged with
  peer, filename, size, hash, status, and timestamp. If MySQL isn't
  reachable, the app falls back to an in-memory history instead of crashing,
  and shows a clear "DB: Offline (in-memory)" status pill in the UI.
- **Form validation and error dialogs** throughout - bad input or network
  failures show a dialog, not a stack trace.
- Built with Java generics: a `Repository<T, ID>` interface for the data
  layer, plus generic `Pair<A, B>` and `Result<T>` utility classes used
  around the GUI.

## Requirements

- **JDK 21** (the full JDK, not a headless/JRE-only package).
- **Maven** - required. First build needs internet access to download JavaFX
  and the MariaDB driver from Maven Central; after that they are cached
  locally (`~/.m2`).
- **MySQL 8.x** (or MariaDB) reachable from the machine running the app.
  The app will still run without it - it just keeps history in memory for
  that session.

## Project structure

```
PeerShare/
├── pom.xml                  Maven project file (JavaFX + JDBC deps, OS profiles)
├── db/schema.sql            MySQL schema for the transfer_history table
├── db.properties            DB connection settings (edit if yours differ)
├── run.sh / run.bat         Launcher scripts (require Maven - see above)
├── src/main/java/peershare/
│   ├── fx/                  JavaFX GUI (Launcher, PeerShareFxApp, MainScreen,
│   │                          AddPeerDialog, NetworkTestDialog,
│   │                          DownloadProgressDialog, Theme)
│   ├── db/                  Repository<T,ID>, TransferHistoryRepository, TransferRecord, DatabaseConfig
│   ├── util/                Generic Pair<A,B> and Result<T>
│   └── *.java                Networking/crypto core: Peer, PeerDiscovery,
│                              SharedFileRegistry, FileShareServer,
│                              FileShareClient, TransferTask, CryptoUtil,
│                              HashUtil, Validation
├── src/main/resources/peershare/fx/   Light/dark CSS theme
└── src/test/java/peershare/  Unit tests (JUnit 5)
```

This is a standard Maven layout (`src/main/java`, `src/test/java`,
`pom.xml` at the root), so it opens and runs directly in IntelliJ IDEA:
**File → Open**, point it at the `PeerShare` folder, and let IntelliJ import
the Maven project.

No sample or placeholder data ships with this project - the `shared` and
`downloads` folders are created empty on first run, wherever you point the
Startup dialog at.

## Setting up MySQL

```bash
mysql -u root < db/schema.sql
```

This creates the `peershare` database, a `peershare` user (password
`peershare_pw`), and the `transfer_history` table. If you use different
credentials, either edit `db.properties` or edit `db/schema.sql` before
running it.

`db.properties`:

```properties
db.host=127.0.0.1
db.port=3306
db.name=peershare
db.user=peershare
db.password=peershare_pw
```

**Why 127.0.0.1 and not localhost?** MySQL treats `localhost` as a Unix-socket
connection and `127.0.0.1` as a real TCP/IP connection. JDBC always uses TCP,
so the schema grants the `peershare` user for both hosts. Using `127.0.0.1`
here guarantees the TCP path is taken and avoids intermittent "Access denied"
errors on servers with `skip-name-resolve` or unusual reverse-DNS settings.

If MySQL isn't set up or isn't reachable when the app starts, PeerShare
still opens - the top-right status shows **"Database: unavailable - using
in-memory history"**, and history for that session just won't persist
across restarts.

## Running it

**One-time setup (recommended):** run `./setup-linux.sh` (Linux, as root/sudo)
or `setup-windows.bat` (Windows, as Administrator) once per machine. This
opens the UDP discovery port and a TCP transfer port in your firewall.
See "Peer discovery across different networks" below for details.

**From IntelliJ:** open the project, let Maven finish importing (this
downloads the JavaFX and MariaDB driver jars - needs internet the first
time), then run `peershare.fx.Launcher`'s `main` method. (Not
`PeerShareFxApp` directly - see the note in `Launcher.java` on why JavaFX
needs a separate non-`Application` entry-point class when run off a plain
classpath/jar.)

**From a terminal:**

```bash
./run.sh          # Linux
run.bat           # Windows
```

Both scripts require Maven on your `PATH` now (see Requirements above).
They try the already-built fat jar first (`target/peershare.jar`), build it
with `mvn package` if that's available and it isn't built yet, and fall
back to `mvn javafx:run` if packaging fails for any reason. Either way you
end up with the GUI open - starting with the splash screen, then Setup,
then the main window.

Your peer name, port, shared/download folders, and light/dark theme choice
are remembered in `~/.peershare/prefs.properties` and pre-filled next time -
set them once and they stick. The peer name defaults to
`<your-username>-<your-hostname>` on first launch. There's also a "Find Free
Port" button next to the port field if the default port is already taken.

**To try it peer-to-peer on one machine**, launch it twice (two terminals).
Give the second instance a different TCP port and different shared/download
folders in the Setup screen so the two instances don't collide.

## Running the tests

```bash
mvn test
```

Covers:
- `HashUtilTest` - SHA-256 hashing, including known/verified digests
- `CryptoUtilTest` - RSA key generation, AES key wrap/unwrap, and a full
  AES stream encrypt/decrypt round trip
- `SharedFileRegistryTest` - folder scanning, picking files from outside the
  shared folder, duplicate-name handling
- `PairAndResultTest` - the generic utility classes
- `TransferHistoryRepositoryTest` - the generic `Repository<T, ID>`
  contract, both in-memory fallback and (when MySQL is reachable) against a
  real database



## Running multiple instances (local two-peer demo)

Each instance must use a **different TCP port** and preferably different shared/download folders
(the Startup dialog already randomises the port for you).

1. Launch the first instance (`./run.sh` or from IntelliJ).
2. Launch a second instance the same way.
3. In the second instance's Setup screen, change the peer name (e.g. `Peer-B`) and accept the random port.
4. Both windows stay visible because **"Keep this window on top" is now on by default**.
   Un-check it on either window if you no longer want that behaviour.

If a window still disappears behind others on your Linux desktop, click the checkbox again
(or use your window manager’s “always on top” / pin action).

## Kali Linux notes (MariaDB + GUI tools)

Check whether MariaDB is already installed:

```bash
dpkg -l | grep -E 'mariadb|mysql' | head
systemctl status mariadb   # or: systemctl status mysql
```

Install (if missing):

```bash
sudo apt update
sudo apt install -y mariadb-server mariadb-client
sudo systemctl enable --now mariadb
sudo mysql_secure_installation   # optional but recommended
```

Apply the PeerShare schema:

```bash
sudo mysql -u root < db/schema.sql
# (or mysql -u root -p < db/schema.sql if you set a root password)
```

Open a GUI to inspect / track the `transfer_history` table:

- **DBeaver** (recommended, free): `sudo apt install dbeaver-ce` or download from https://dbeaver.io
- **MySQL Workbench** / **MariaDB Workbench** (if available in your repos)
- Or just the CLI: `mysql -u peershare -ppeershare_pw peershare` then `SELECT * FROM transfer_history ORDER BY occurred_at DESC;`

Connection settings inside any GUI tool:

- Host: `127.0.0.1`
- Port: `3306`
- User: `peershare`
- Password: `peershare_pw`
- Database: `peershare`

## Windows second machine (for real peer-to-peer testing)

On the Windows PC:

1. Install **Eclipse Temurin JDK 21** (or any OpenJDK 21) from https://adoptium.net
2. Install **IntelliJ IDEA Community** from https://www.jetbrains.com/idea/download/
3. Install **MariaDB** from https://mariadb.org/download/ (or XAMPP which includes MariaDB)
4. Copy the whole `PeerShare` folder (or `git clone` your GitHub repo) to the Windows machine.
5. Open the folder in IntelliJ → let Maven import (downloads JavaFX + the JDBC driver - needs internet the first time) → run `peershare.fx.Launcher`.
   Or double-click / run `run.bat` (also requires Maven on `PATH` - see Requirements above).
6. Run the schema once: open a MariaDB client and execute `db/schema.sql` (or use the same `mysql` command if the client is on PATH).
7. Run `setup-windows.bat` **as Administrator** once to open the firewall ports (see "Peer discovery across different networks" below).

Both machines must be on the **same LAN** (same Wi-Fi / same switch). Windows Firewall will usually ask the first time the app opens a port — allow it for private networks.

Give each machine a different peer name and different TCP port. They should discover each other within a couple of seconds via the UDP broadcast.

## Peer discovery across different networks

Peer discovery relies on **UDP broadcast**, which only reaches other devices
on the same physical/logical LAN segment. It works great on a plain home
Wi-Fi router (the default setup this project targets), but is commonly
**blocked or isolated** on:

- Campus/enterprise Wi-Fi (client isolation is standard on these)
- VPNs (most don't forward broadcast traffic)
- Phone hotspots (many isolate connected clients from each other)
- Some public/guest networks

**How to tell:** the "Discovered Peers" panel on the left shows a banner at
the top describing your current network. It stays neutral on what looks
like a private LAN (`192.168.x.x`, `10.x.x.x`, `172.16-31.x.x`), and turns
orange with a warning if your network doesn't look like a private LAN, or
no usable interface was found at all. Click **"Test My Network"** for a more
detailed report, including a loopback UDP self-test that checks whether a
local firewall/antivirus is blocking UDP sockets outright (independent of
the LAN-vs-not-LAN question).

**If broadcast discovery isn't finding peers**, use **"Add Peer Manually..."**
in the same panel: enter the peer's name, IP address, and TCP port (they can
read their own IP/port off their own window title or "Identity" status
line). This bypasses broadcast entirely and connects straight to that
address. Manually-added peers:

- Show up in the peer list tagged `[manual]`
- Are **not** auto-evicted the way broadcast-discovered peers are when they
  go quiet - if a manual peer becomes unreachable, its Download tab will
  just fail with a connection error until it's back
- Persist across restarts (stored in `~/.peershare/manual-peers.txt`), so
  you don't have to re-add the same peer every session
- Can be removed with **"Remove Selected Peer"** (this only works on manual
  peers - broadcast-discovered peers drop off on their own)

Run `setup-linux.sh` / `setup-windows.bat` once per machine to open the
UDP discovery port (9876) and a TCP transfer port in your firewall - this
often fixes discovery/connection issues on a plain home network even when
the "same LAN" assumption otherwise holds. It does **not** help on networks
with genuine client isolation (campus Wi-Fi etc); manual peer add is the
only workaround there, and even that requires the peers to actually be able
to open a TCP connection to each other, which client-isolated networks may
also block.

## Known limitations

- The SENT side of `transfer_history` resolves the peer's name via the live
  discovery registry; if a peer has just gone offline (or a connection
  comes from outside normal LAN discovery), that record falls back to
  logging the raw IP address instead of a name.
- Discovery is LAN-broadcast based, so it won't find peers across
  subnets/VLANs or over the open internet by design; "Add Peer Manually"
  works around this only when the underlying TCP connection is actually
  reachable (see "Peer discovery across different networks" above) - it
  can't bypass a firewall or NAT that blocks the connection itself.
- Manual peers are unauthenticated - anyone who knows (or guesses) a
  reachable IP/port can be added, and peer *names* in general (broadcast or
  manual) are just strings, not verified identities. Fine for a LAN demo,
  not something to rely on for anything sensitive.
- If `mvn package` or `run.sh`/`run.bat` fail on your platform, try
  forcing the JavaFX classifier with `-Djavafx.platform=win` (or `linux` / `mac`).
