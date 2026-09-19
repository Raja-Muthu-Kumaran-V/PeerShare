package peershare.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import peershare.*;
import peershare.db.TransferHistoryRepository;
import peershare.db.TransferRecord;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Main application window: peer list, shared files, downloads, transfer history, and activity log.
 */
public class MainScreen {

    private final Stage stage;
    private final PeerShareFxApp.Config config;
    private final SharedFileRegistry registry;
    private final PeerDiscovery discovery;
    private final FileShareServer server;
    private final TransferHistoryRepository historyRepo;
    private final Path downloadDir;
    private final ExecutorService transferPool;

    private static final File MANUAL_PEERS_FILE = new File(System.getProperty("user.home"), ".peershare" + File.separator + "manual-peers.txt");

    private final ObservableList<Peer> peerItems = FXCollections.observableArrayList();
    private final ListView<Peer> peerList = new ListView<>(peerItems);
    private Peer selectedRemotePeer;

    private final ObservableList<SharedFileRow> sharedFileRows = FXCollections.observableArrayList();
    private final ObservableList<RemoteFileRow> remoteFileRows = FXCollections.observableArrayList();
    private final ObservableList<TransferRecord> historyRows = FXCollections.observableArrayList();

    private final TextArea logArea = new TextArea();
    private final Label identityLabel = new Label();
    private final Label peerCountLabel = new Label();
    private final Label dbStatusPill = new Label();
    private final Label downloadPeerLabel = new Label("Select a peer on the left, then click Refresh File List.");

    private BorderPane root;

    public MainScreen(Stage stage, PeerShareFxApp.Config config, SharedFileRegistry registry, PeerDiscovery discovery,
                       FileShareServer server, TransferHistoryRepository historyRepo, Path downloadDir) {
        this.stage = stage;
        this.config = config;
        this.registry = registry;
        this.discovery = discovery;
        this.server = server;
        this.historyRepo = historyRepo;
        this.downloadDir = downloadDir;

        for (String[] p : loadSavedManualPeers()) {
            try {
                discovery.addManualPeer(p[0], p[1], Integer.parseInt(p[2]));
            } catch (NumberFormatException ignored) { }
        }

        AtomicInteger threadCounter = new AtomicInteger(1);
        ThreadFactory namedFactory = r -> new Thread(r, "Transfer-" + threadCounter.getAndIncrement());
        this.transferPool = Executors.newCachedThreadPool(namedFactory);

        build();
        redirectSystemOutToLog();
    }

    public Parent getRoot() { return root; }

    public void afterShown() {
        Timeline poll = new Timeline(new KeyFrame(Duration.seconds(2), e -> refreshPeerList()));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
        refreshPeerList();
        refreshSharedFiles();
        refreshHistory();

        if (!historyRepo.isDatabaseAvailable()) {
            log("Database not reachable at startup (" + historyRepo.getUnavailableReason() + "). " +
                    "Transfer history will be kept in memory only until you retry the connection.");
        }

        stage.setOnCloseRequest(e -> { e.consume(); shutdown(); });
    }

    // ------------------------------------------------------------ Layout

    private void build() {
        root = new BorderPane();
        root.getStyleClass().add("ps-page");
        root.setTop(buildTopBar());

        HBox center = new HBox(12);
        center.setPadding(new Insets(12));
        center.getChildren().addAll(buildPeersColumn(), buildMainColumn());
        HBox.setHgrow(center.getChildren().get(1), Priority.ALWAYS);
        root.setCenter(center);
    }

    private VBox buildTopBar() {
        identityLabel.setText(config.peerName() + "   |   TCP " + config.tcpPort() + "   |   UDP " + PeerDiscovery.DISCOVERY_PORT);
        identityLabel.setStyle("-fx-font-weight: bold;");

        dbStatusPill.getStyleClass().add("ps-pill");
        updateDbStatusLabel();

        ToggleButton themeToggle = new ToggleButton(Theme.isDark() ? "Light Mode" : "Dark Mode");
        themeToggle.getStyleClass().add("ps-theme-toggle");
        themeToggle.setSelected(Theme.isDark());
        themeToggle.setOnAction(e -> {
            Theme.toggle();
            themeToggle.setText(Theme.isDark() ? "Light Mode" : "Dark Mode");
        });

        HBox statusRow = new HBox(16, identityLabel, spacer(), dbStatusPill, themeToggle);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(statusRow);
        top.getStyleClass().add("ps-topbar");
        return top;
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private VBox buildPeersColumn() {
        Label header = new Label("Discovered Peers");
        header.getStyleClass().add("ps-card-title");
        peerCountLabel.getStyleClass().add("ps-subtle");

        peerList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Peer p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setGraphic(null); setText(null); return; }
                Circle dot = new Circle(4);
                dot.getStyleClass().add("ps-dot-online");
                Label name = new Label(p.getName() + (p.isManual() ? "  [manual]" : ""));
                Label addr = new Label(p.getHost() + ":" + p.getPort());
                addr.getStyleClass().add("ps-subtle");
                VBox text = new VBox(2, name, addr);
                HBox row = new HBox(8, dot, text);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        peerList.getSelectionModel().selectedItemProperty().addListener((obs, old, newly) -> {
            if (newly != null && !newly.equals(selectedRemotePeer)) {
                selectedRemotePeer = newly;
                remoteFileRows.clear();
                downloadPeerLabel.setText("Files shared by " + newly + ":");
            }
        });
        peerList.setPrefHeight(220);

        NetworkUtil.NetworkClass netClass = NetworkUtil.classify();
        Label banner = new Label(netClass.description);
        banner.setWrapText(true);
        if (netClass != NetworkUtil.NetworkClass.PRIVATE_LAN) {
            banner.getStyleClass().add("ps-banner-warning");
        } else {
            banner.getStyleClass().add("ps-subtle");
        }

        Button addPeer = new Button("Add Peer Manually...");
        Button removePeer = new Button("Remove Peer");
        removePeer.getStyleClass().add("ps-danger");
        Button testNetwork = new Button("Test My Network");
        addPeer.setOnAction(e -> addPeerManually());
        removePeer.setOnAction(e -> removeSelectedPeer());
        testNetwork.setOnAction(e -> NetworkTestDialog.show(stage));
        addPeer.setMaxWidth(Double.MAX_VALUE);
        removePeer.setMaxWidth(Double.MAX_VALUE);
        testNetwork.setMaxWidth(Double.MAX_VALUE);

        VBox peersCard = new VBox(8, header, peerCountLabel, banner, peerList, addPeer, removePeer);
        peersCard.getStyleClass().add("ps-card");

        VBox networkCard = new VBox(6, sectionTitle("Network Status"), testNetwork);
        networkCard.getStyleClass().add("ps-card");

        VBox column = new VBox(12, peersCard, networkCard);
        column.setPrefWidth(280);
        column.setMinWidth(240);
        return column;
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("ps-card-title");
        return l;
    }

    private VBox buildMainColumn() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab sharedTab = new Tab("My Shared Files", buildSharedFilesTab());
        Tab downloadTab = new Tab("Download", buildDownloadTab());
        Tab historyTab = new Tab("Transfer History", buildHistoryTab());
        tabs.getTabs().addAll(sharedTab, downloadTab, historyTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        Label logHeader = new Label("Activity Log");
        logHeader.getStyleClass().add("ps-card-title");
        logArea.setEditable(false);
        logArea.getStyleClass().add("ps-log");
        logArea.setPrefRowCount(6);
        VBox logCard = new VBox(6, logHeader, logArea);
        logCard.getStyleClass().add("ps-card");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        VBox column = new VBox(12, tabs, logCard);
        HBox.setHgrow(column, Priority.ALWAYS);
        return column;
    }

    // ------------------------------------------------------------ Shared Files tab

    private record SharedFileRow(String name, String size, String source) {}

    private VBox buildSharedFilesTab() {
        TableView<SharedFileRow> table = new TableView<>(sharedFileRows);
        TableColumn<SharedFileRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name()));
        TableColumn<SharedFileRow, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().size()));
        TableColumn<SharedFileRow, String> sourceCol = new TableColumn<>("Shared From");
        sourceCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().source()));
        table.getColumns().addAll(List.of(nameCol, sizeCol, sourceCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        installFileDropHandler(table);

        Button shareFile = new Button("Share File");
        shareFile.getStyleClass().add("ps-primary");
        Button shareFolder = new Button("Share Folder");
        shareFolder.getStyleClass().add("ps-success-btn");
        Button stopSharing = new Button("Stop Sharing");
        stopSharing.getStyleClass().add("ps-danger");
        Button refresh = new Button("Refresh");

        shareFile.setOnAction(e -> shareFile());
        shareFolder.setOnAction(e -> shareFolder());
        stopSharing.setOnAction(e -> stopSharing(table));
        refresh.setOnAction(e -> refreshSharedFiles());

        HBox buttons = new HBox(8, shareFile, shareFolder, stopSharing, refresh);
        VBox panel = new VBox(8, buttons, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return panel;
    }

    private void shareFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a file to share (from anywhere on your computer)");
        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) return;
        if (!chosen.canRead()) { showError("PeerShare doesn't have permission to read: " + chosen); return; }
        String key = registry.addPickedFile(chosen.toPath().toAbsolutePath().normalize());
        log("Now sharing '" + key + "' from: " + chosen.getAbsolutePath());
        refreshSharedFiles();
    }

    private void shareFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a folder - every file directly inside it will be shared");
        File dir = chooser.showDialog(stage);
        if (dir == null) return;
        File[] entries = dir.listFiles(File::isFile);
        if (entries == null || entries.length == 0) { showError("That folder has no files directly inside it."); return; }
        int shared = 0;
        for (File f : entries) {
            if (!f.canRead()) { log("Skipped (not readable): " + f.getName()); continue; }
            String key = registry.addPickedFile(f.toPath().toAbsolutePath().normalize());
            log("Now sharing '" + key + "' from: " + f.getAbsolutePath());
            shared++;
        }
        log("Shared " + shared + " file(s) from " + dir);
        refreshSharedFiles();
    }

    private void stopSharing(TableView<SharedFileRow> table) {
        SharedFileRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) { showError("Select a shared file first."); return; }
        if (!registry.isPicked(row.name())) {
            showError("'" + row.name() + "' is auto-shared from your shared/ folder.\nMove or delete it from that folder to stop sharing it.");
            return;
        }
        registry.removePickedFile(row.name());
        log("Stopped sharing: " + row.name());
        refreshSharedFiles();
    }

    private void refreshSharedFiles() {
        sharedFileRows.clear();
        for (String name : registry.listFileNames()) {
            String size = peershare.ProgressBar.formatBytes(registry.sizeOf(name));
            String source = registry.isPicked(name) ? registry.getSourcePath(name).toString() : "(shared folder)";
            sharedFileRows.add(new SharedFileRow(name, size, source));
        }
    }

    private void installFileDropHandler(TableView<SharedFileRow> table) {
        table.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        table.setOnDragDropped((DragEvent event) -> {
            boolean success = false;
            if (event.getDragboard().hasFiles()) {
                int shared = 0;
                for (File f : event.getDragboard().getFiles()) {
                    if (f.isDirectory()) {
                        File[] entries = f.listFiles(File::isFile);
                        if (entries != null) {
                            for (File entry : entries) {
                                if (!entry.canRead()) { log("Skipped (not readable): " + entry.getName()); continue; }
                                String key = registry.addPickedFile(entry.toPath().toAbsolutePath().normalize());
                                log("Now sharing '" + key + "' from: " + entry.getAbsolutePath());
                                shared++;
                            }
                        }
                    } else if (f.isFile() && f.canRead()) {
                        String key = registry.addPickedFile(f.toPath().toAbsolutePath().normalize());
                        log("Now sharing '" + key + "' from: " + f.getAbsolutePath());
                        shared++;
                    } else {
                        log("Skipped (not readable): " + f.getName());
                    }
                }
                if (shared > 0) { refreshSharedFiles(); success = true; }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    // ------------------------------------------------------------ Download tab

    private record RemoteFileRow(String name, String size, long rawSize) {}

    private VBox buildDownloadTab() {
        TableView<RemoteFileRow> table = new TableView<>(remoteFileRows);
        TableColumn<RemoteFileRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name()));
        TableColumn<RemoteFileRow, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().size()));
        table.getColumns().addAll(List.of(nameCol, sizeCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        downloadPeerLabel.getStyleClass().add("ps-subtle");
        Button refreshList = new Button("Refresh File List");
        Button download = new Button("Download Selected");
        download.getStyleClass().add("ps-primary");
        refreshList.setOnAction(e -> refreshRemoteFileList());
        download.setOnAction(e -> downloadSelected(table));

        HBox buttons = new HBox(8, refreshList, download);
        VBox panel = new VBox(8, downloadPeerLabel, buttons, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return panel;
    }

    private void refreshRemoteFileList() {
        if (selectedRemotePeer == null) { showError("Select a peer from the list on the left first."); return; }
        Peer peer = selectedRemotePeer;
        downloadPeerLabel.setText("Files shared by " + peer + ":");
        new Thread(() -> {
            try {
                Map<String, Long> files = FileShareClient.listRemoteFiles(peer);
                Platform.runLater(() -> {
                    remoteFileRows.clear();
                    for (var e : files.entrySet()) {
                        remoteFileRows.add(new RemoteFileRow(e.getKey(), peershare.ProgressBar.formatBytes(e.getValue()), e.getValue()));
                    }
                    log("Listed " + files.size() + " file(s) from " + peer);
                });
            } catch (IOException ex) {
                Platform.runLater(() -> showError("Could not reach " + peer + ": " + ex.getMessage()));
            }
        }, "ListRemoteFiles").start();
    }

    private void downloadSelected(TableView<RemoteFileRow> table) {
        if (selectedRemotePeer == null) { showError("Select a peer first."); return; }
        RemoteFileRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) { showError("Select a file to download."); return; }
        String filename = row.name();
        Peer peer = selectedRemotePeer;

        log("Downloading '" + filename + "' from " + peer + "...");
        DownloadProgressDialog progressDialog = new DownloadProgressDialog(stage, filename);
        progressDialog.show();

        transferPool.submit(new TransferTask(peer, filename, downloadDir,
                (received, total) -> progressDialog.updateProgress(received, total),
                event -> {
                    onTransferEvent(event);
                    boolean success = event.status() == TransferEvent.Status.SUCCESS;
                    File resultFile = success ? downloadDir.resolve(filename).toFile() : null;
                    String message = switch (event.status()) {
                        case SUCCESS -> "Download complete!";
                        case FAILED -> "Transfer failed";
                        case HASH_MISMATCH -> "Hash mismatch - file discarded";
                    };
                    progressDialog.onFinished(success, message, resultFile);
                }));
    }

    // ------------------------------------------------------------ History tab

    private VBox buildHistoryTab() {
        TableView<TransferRecord> table = new TableView<>(historyRows);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

        TableColumn<TransferRecord, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getOccurredAt().format(fmt)));
        TableColumn<TransferRecord, String> dirCol = new TableColumn<>("Direction");
        dirCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().getDirection())));
        TableColumn<TransferRecord, String> peerCol = new TableColumn<>("Peer");
        peerCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getPeerName() + " (" + c.getValue().getPeerAddress() + ")"));
        TableColumn<TransferRecord, String> fileCol = new TableColumn<>("File");
        fileCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getFilename()));
        TableColumn<TransferRecord, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(peershare.ProgressBar.formatBytes(c.getValue().getSizeBytes())));
        TableColumn<TransferRecord, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().getStatus())));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); return; }
                Label pill = new Label(status);
                pill.getStyleClass().add("ps-pill");
                pill.getStyleClass().add(switch (status) {
                    case "SUCCESS" -> "ps-pill-success";
                    case "FAILED" -> "ps-pill-danger";
                    default -> "ps-pill-warning";
                });
                setGraphic(pill);
            }
        });

        table.getColumns().addAll(List.of(timeCol, dirCol, peerCol, fileCol, sizeCol, statusCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        Button refresh = new Button("Refresh");
        Button retryDb = new Button("Retry DB");
        refresh.setOnAction(e -> refreshHistory());
        retryDb.setOnAction(e -> {
            boolean ok = historyRepo.retryConnection();
            updateDbStatusLabel();
            log(ok ? "Reconnected to the database." : "Still could not reach the database: " + historyRepo.getUnavailableReason());
            refreshHistory();
        });
        HBox buttons = new HBox(8, refresh, retryDb);

        VBox panel = new VBox(8, buttons, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return panel;
    }

    private void refreshHistory() {
        new Thread(() -> {
            List<TransferRecord> records = historyRepo.findAll();
            Platform.runLater(() -> {
                historyRows.setAll(records);
                updateDbStatusLabel();
            });
        }, "RefreshHistory").start();
    }

    private void updateDbStatusLabel() {
        boolean up = historyRepo.isDatabaseAvailable();
        dbStatusPill.setText(up ? "DB: Connected" : "DB: Offline (in-memory)");
        dbStatusPill.getStyleClass().removeAll("ps-pill-success", "ps-pill-danger");
        dbStatusPill.getStyleClass().add(up ? "ps-pill-success" : "ps-pill-danger");
    }

    // ------------------------------------------------------------ Shared plumbing

    public void onTransferEvent(TransferEvent event) {
        historyRepo.save(new TransferRecord(null, event.peerName(), event.peerAddress(), event.filename(),
                event.sizeBytes(),
                event.direction() == TransferEvent.Direction.SENT ? TransferRecord.Direction.SENT : TransferRecord.Direction.RECEIVED,
                switch (event.status()) {
                    case SUCCESS -> TransferRecord.Status.SUCCESS;
                    case FAILED -> TransferRecord.Status.FAILED;
                    case HASH_MISMATCH -> TransferRecord.Status.HASH_MISMATCH;
                },
                event.sha256(), event.occurredAt()));
        Platform.runLater(this::refreshHistory);
    }

    private void refreshPeerList() {
        List<Peer> current = discovery.getActivePeers();
        Peer previouslySelected = peerList.getSelectionModel().getSelectedItem();
        peerItems.setAll(current);
        peerCountLabel.setText(current.size() + " online");
        if (previouslySelected != null && current.contains(previouslySelected)) {
            peerList.getSelectionModel().select(previouslySelected);
        }
    }

    private void addPeerManually() {
        AddPeerDialog.show(stage).ifPresent(result -> {
            discovery.addManualPeer(result.name(), result.host(), result.port());
            saveManualPeer(result.name(), result.host(), result.port());
            log("Added manual peer: " + result.name() + " (" + result.host() + ":" + result.port() + ")");
            refreshPeerList();
        });
    }

    private void removeSelectedPeer() {
        Peer selected = peerList.getSelectionModel().getSelectedItem();
        if (selected == null) { showError("Select a peer first."); return; }
        if (!selected.isManual()) {
            showError("Only manually-added peers can be removed here.\nDiscovered peers drop off automatically when they go offline.");
            return;
        }
        discovery.removePeer(selected.getId());
        removeSavedManualPeer(selected);
        log("Removed manual peer: " + selected);
        refreshPeerList();
    }

    private void saveManualPeer(String name, String host, int port) {
        try {
            File dir = MANUAL_PEERS_FILE.getParentFile();
            if (!dir.isDirectory()) dir.mkdirs();
            try (FileWriter w = new FileWriter(MANUAL_PEERS_FILE, true)) {
                w.write(name + "|" + host + "|" + port + System.lineSeparator());
            }
        } catch (IOException e) {
            log("Could not persist manual peer: " + e.getMessage());
        }
    }

    private static List<String[]> loadSavedManualPeers() {
        List<String[]> result = new ArrayList<>();
        if (!MANUAL_PEERS_FILE.isFile()) return result;
        try {
            for (String line : Files.readAllLines(MANUAL_PEERS_FILE.toPath())) {
                String[] parts = line.split("\\|");
                if (parts.length == 3) result.add(parts);
            }
        } catch (IOException ignored) { }
        return result;
    }

    private void removeSavedManualPeer(Peer peer) {
        List<String[]> remaining = loadSavedManualPeers().stream()
                .filter(p -> !(p[1].equals(peer.getHost()) && p[2].equals(String.valueOf(peer.getPort()))))
                .collect(Collectors.toList());
        try {
            File dir = MANUAL_PEERS_FILE.getParentFile();
            if (!dir.isDirectory()) dir.mkdirs();
            StringBuilder sb = new StringBuilder();
            for (String[] p : remaining) sb.append(p[0]).append('|').append(p[1]).append('|').append(p[2]).append(System.lineSeparator());
            Files.writeString(MANUAL_PEERS_FILE.toPath(), sb.toString());
        } catch (IOException e) {
            log("Could not update manual peers file: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message);
        a.setHeaderText("PeerShare");
        a.initOwner(stage);
        a.initModality(javafx.stage.Modality.WINDOW_MODAL);
        a.setResizable(false);
        a.showAndWait();
    }

    private void log(String message) {
        System.out.println(message);
    }

    private void shutdown() {
        log("Shutting down...");
        transferPool.shutdown();
        discovery.stop();
        server.stop();
        Platform.exit();
        System.exit(0);
    }

    /** Mirrors System.out into the Activity Log (in addition to the real console) so every
     *  handshake/progress/hash line the networking layer already prints shows up in the GUI too. */
    private void redirectSystemOutToLog() {
        PrintStream realOut = System.out;
        PrintStream tee = new PrintStream(new java.io.OutputStream() {
            private final StringBuilder buffer = new StringBuilder();
            @Override public void write(int b) {
                char c = (char) b;
                if (c == '\n') {
                    String line = buffer.toString();
                    buffer.setLength(0);
                    Platform.runLater(() -> logArea.appendText(line + "\n"));
                } else if (c != '\r') {
                    buffer.append(c);
                }
            }
        }, true, StandardCharsets.UTF_8);
        System.setOut(new PrintStream(new java.io.OutputStream() {
            @Override public void write(int b) throws IOException {
                realOut.write(b);
                tee.write(b);
            }
        }, true, StandardCharsets.UTF_8));
    }
}
