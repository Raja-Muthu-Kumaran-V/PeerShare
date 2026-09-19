package peershare.fx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import peershare.*;
import peershare.db.DatabaseConfig;
import peershare.db.TransferHistoryRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Properties;
import java.util.function.Function;

/**
 * PeerShare JavaFX application.
 * Flow: splash -> setup form -> start networking -> main window.
 */
public class PeerShareFxApp extends Application {

    public record Config(String peerName, int tcpPort, String sharedDir, String downloadDir) {}

    private static final File PREFS_DIR = new File(System.getProperty("user.home"), ".peershare");
    private static final File PREFS_FILE = new File(PREFS_DIR, "prefs.properties");

    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("PeerShare");
        showSplash();
    }

    // ------------------------------------------------------------ Splash screen

    private void showSplash() {
        VBox root = new VBox(18);
        root.getStyleClass().add("ps-page");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        Text brand = new Text("Peer");
        brand.getStyleClass().add("ps-brand");
        Text brand2 = new Text("Share");
        brand2.getStyleClass().addAll("ps-brand", "ps-brand-accent");
        TextFlow brandFlow = new TextFlow(brand, brand2);
        brandFlow.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Label tagline = new Label("Share  \u00b7  Connect  \u00b7  Transfer");
        tagline.getStyleClass().add("ps-tagline");

        Label desc = new Label("Decentralized file sharing. Secure. Simple. Fast.");
        desc.getStyleClass().add("ps-subtle");

        Button getStarted = new Button("Get Started  \u2192");
        getStarted.getStyleClass().add("ps-primary");
        getStarted.setOnAction(e -> showSetup());

        Label version = new Label("v2.0.0 (JavaFX)");
        version.getStyleClass().add("ps-subtle");

        root.getChildren().addAll(brandFlow, tagline, desc, getStarted);
        VBox.setMargin(getStarted, new Insets(16, 0, 0, 0));

        BorderPane page = new BorderPane(root);
        BorderPane.setAlignment(version, Pos.BOTTOM_LEFT);
        BorderPane.setMargin(version, new Insets(0, 0, 16, 16));
        page.setBottom(version);
        page.getStyleClass().add("ps-page");

        Scene scene = new Scene(page, 760, 480);
        Theme.track(scene);
        stage.setScene(scene);
        stage.show();
    }

    // ------------------------------------------------------------ Setup screen

    private void showSetup() {
        Properties prefs = loadPrefs();

        TextField nameField = new TextField(prefs.getProperty("peerName", defaultPeerName()));
        TextField portField = new TextField(prefs.getProperty("tcpPort", String.valueOf(50000 + (int) (Math.random() * 5000))));
        TextField sharedDirField = new TextField(prefs.getProperty("sharedDir",
                System.getProperty("user.home") + File.separator + "PeerShare" + File.separator + "shared"));
        TextField downloadDirField = new TextField(prefs.getProperty("downloadDir",
                System.getProperty("user.home") + File.separator + "PeerShare" + File.separator + "downloads"));
        Label errorLabel = new Label(" ");
        errorLabel.getStyleClass().add("ps-pill-danger");

        Button findPort = new Button("Find Free Port");
        findPort.setOnAction(e -> {
            try (ServerSocket s = new ServerSocket(0)) {
                portField.setText(String.valueOf(s.getLocalPort()));
                errorLabel.setText(" ");
            } catch (IOException ex) {
                errorLabel.setText("Could not find a free port: " + ex.getMessage());
            }
        });

        Button browseShared = new Button("Browse...");
        browseShared.setOnAction(e -> browseFolder(sharedDirField));
        Button browseDownload = new Button("Browse...");
        browseDownload.setOnAction(e -> browseFolder(downloadDirField));

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        int r = 0;
        r = addFormRow(form, r, "Your Peer Name", nameField, null);
        r = addFormRow(form, r, "TCP Port", portField, findPort);
        r = addFormRow(form, r, "Shared Folder", sharedDirField, browseShared);
        r = addFormRow(form, r, "Download Folder", downloadDirField, browseDownload);
        GridPane.setColumnSpan(nameField, 1);

        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> Platform.exit());
        Button start = new Button("Start PeerShare  \u2192");
        start.getStyleClass().add("ps-primary");
        start.setOnAction(e -> {
            String name = nameField.getText().trim();
            String portText = portField.getText().trim();
            String sharedDir = sharedDirField.getText().trim();
            String downloadDir = downloadDirField.getText().trim();
            int port;
            if (!Validation.isValidPeerName(name)) {
                errorLabel.setText("Peer name can't be empty, over 40 chars, or contain '|'.");
                return;
            }
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException ex) {
                errorLabel.setText("TCP port must be a whole number.");
                return;
            }
            if (!Validation.isValidPort(port)) {
                errorLabel.setText("TCP port must be between 1 and 65535.");
                return;
            }
            if (sharedDir.isEmpty() || downloadDir.isEmpty()) {
                errorLabel.setText("Shared and download folders are both required.");
                return;
            }
            Config config = new Config(name, port, sharedDir, downloadDir);
            savePrefs(config);
            launchBackend(config);
        });

        HBox buttons = new HBox(10, cancel, start);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox formBox = new VBox(18,
                titleBlock("Welcome to PeerShare", "Let's set up your peer details and folders."),
                form, errorLabel, buttons);
        formBox.setPadding(new Insets(30));
        formBox.setMaxWidth(520);
        VBox.setVgrow(form, Priority.NEVER);

        BorderPane page = new BorderPane();
        page.getStyleClass().add("ps-page");
        BorderPane center = new BorderPane(formBox);
        BorderPane.setAlignment(formBox, Pos.CENTER);
        page.setCenter(center);

        Scene scene = new Scene(page, 620, 520);
        Theme.track(scene);
        stage.setScene(scene);
    }

    private VBox titleBlock(String title, String subtitle) {
        Label t = new Label(title);
        t.setFont(Font.font(null, FontWeight.BOLD, 22));
        Label s = new Label(subtitle);
        s.getStyleClass().add("ps-subtle");
        return new VBox(4, t, s);
    }

    private int addFormRow(GridPane form, int row, String label, TextField field, Button action) {
        Label l = new Label(label);
        l.getStyleClass().add("ps-field-label");
        form.add(l, 0, row);
        HBox fieldRow = new HBox(8, field);
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setPrefWidth(320);
        if (action != null) fieldRow.getChildren().add(action);
        form.add(fieldRow, 0, row + 1);
        return row + 2;
    }

    private void browseFolder(TextField field) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a folder");
        File current = new File(field.getText().trim());
        if (current.isDirectory()) chooser.setInitialDirectory(current);
        File chosen = chooser.showDialog(stage);
        if (chosen != null) field.setText(chosen.getAbsolutePath());
    }

    private static String defaultPeerName() {
        String username = System.getProperty("user.name", "peer");
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "host";
        }
        return username + "-" + hostname;
    }

    private static Properties loadPrefs() {
        Properties p = new Properties();
        if (PREFS_FILE.isFile()) {
            try (FileInputStream in = new FileInputStream(PREFS_FILE)) {
                p.load(in);
            } catch (IOException ignored) { }
        }
        return p;
    }

    /** Merges with whatever's already on disk (e.g. "theme") rather than overwriting it. */
    private static void savePrefs(Config config) {
        try {
            if (!PREFS_DIR.isDirectory()) PREFS_DIR.mkdirs();
            Properties p = loadPrefs();
            p.setProperty("peerName", config.peerName());
            p.setProperty("tcpPort", String.valueOf(config.tcpPort()));
            p.setProperty("sharedDir", config.sharedDir());
            p.setProperty("downloadDir", config.downloadDir());
            try (FileOutputStream out = new FileOutputStream(PREFS_FILE)) {
                p.store(out, "PeerShare preferences");
            }
        } catch (IOException ignored) {
            // Non-fatal: worst case the user re-enters their settings next launch.
        }
    }

    // ------------------------------------------------------------ Backend bootstrap (mirrors old launch())

    private void launchBackend(Config config) {
        try {
            Files.createDirectories(Path.of(config.sharedDir()));
            Files.createDirectories(Path.of(config.downloadDir()));
        } catch (IOException e) {
            showFatalAlert("Could not create folders: " + e.getMessage());
            return;
        }

        SharedFileRegistry registry = new SharedFileRegistry(config.sharedDir());

        KeyPair rsaKeyPair;
        try {
            rsaKeyPair = CryptoUtil.generateRSAKeyPair();
        } catch (Exception e) {
            showFatalAlert("This JVM does not support RSA key generation: " + e.getMessage());
            return;
        }

        TransferHistoryRepository historyRepo = new TransferHistoryRepository(DatabaseConfig.load());

        PeerDiscovery discovery = new PeerDiscovery(config.peerName(), config.tcpPort());
        Function<String, String> peerNameResolver = remoteAddr ->
                discovery.getActivePeers().stream()
                        .filter(p -> p.getHost().equals(remoteAddr))
                        .findFirst()
                        .map(Peer::getName)
                        .orElse(remoteAddr);

        FileShareServer server = new FileShareServer(config.tcpPort(), registry, rsaKeyPair, null, peerNameResolver);

        try {
            server.start();
        } catch (IOException e) {
            showRetryableAlert("Could not start on TCP port " + config.tcpPort() + ": " + e.getMessage()
                    + "\nTry a different port.");
            return; // stays on the setup screen so the user can pick another port
        }

        boolean discoveryOk = true;
        String discoveryError = null;
        try {
            discovery.start();
        } catch (Exception e) {
            discoveryOk = false;
            discoveryError = e.getMessage();
        }

        MainScreen mainScreen = new MainScreen(stage, config, registry, discovery, server, historyRepo, Path.of(config.downloadDir()));
        server.setListener(mainScreen::onTransferEvent);

        Thread serverThread = new Thread(server, "FileShareServer");
        serverThread.setDaemon(true);
        serverThread.start();

        Scene scene = new Scene(mainScreen.getRoot(), 1200, 720);
        Theme.track(scene);
        stage.setScene(scene);
        stage.setTitle("PeerShare - " + config.peerName() + " (port " + config.tcpPort() + ")");
        stage.centerOnScreen();
        mainScreen.afterShown();

        if (!discoveryOk) {
            final String err = discoveryError;
            Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.WARNING,
                        "Peer discovery could not start: " + err
                                + "\nYou can still be reached by peers who know your address, but 'Discovered Peers' will stay empty.");
                a.setHeaderText("Discovery Warning");
                a.initOwner(stage);
                a.initModality(javafx.stage.Modality.WINDOW_MODAL);
                a.showAndWait();
            });
        }
    }

    private void showFatalAlert(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message);
        a.setHeaderText("PeerShare");
        a.initOwner(stage);
        a.initModality(javafx.stage.Modality.WINDOW_MODAL);
        a.showAndWait();
    }

    private void showRetryableAlert(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message);
        a.setHeaderText("PeerShare");
        a.initOwner(stage);
        a.initModality(javafx.stage.Modality.WINDOW_MODAL);
        a.showAndWait();
    }
}
