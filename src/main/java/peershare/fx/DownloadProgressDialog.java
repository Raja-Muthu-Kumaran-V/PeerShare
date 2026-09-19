package peershare.fx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

/**
 * Non-blocking "Downloading File" modal shown while a TransferTask runs on the
 * transfer pool. Shows filename + size, a progress bar, a
 * step checklist (Connecting / Requesting / Receiving / Verifying / Done),
 * then Open File / Close once finished. All mutator methods are safe to call
 * from a background thread - they hop to the FX thread internally.
 */
public class DownloadProgressDialog {

    private final Stage stage;
    private final Label sizeLabel;
    private final ProgressBar progressBar;
    private final Label percentLabel;
    private final Label stepConnecting, stepRequesting, stepReceiving, stepVerifying, stepDone;
    private final Button openFile, close;
    private File resultFile;

    public DownloadProgressDialog(Window owner, String filename) {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE); // non-blocking: user can keep using the app while it downloads
        stage.setTitle("Downloading File");

        Label title = new Label("Downloading File");
        title.getStyleClass().add("ps-card-title");
        Label nameLabel = new Label(filename);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        sizeLabel = new Label("Starting...");
        sizeLabel.getStyleClass().add("ps-subtle");

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        percentLabel = new Label("0%");

        stepConnecting = stepLabel("Connecting to peer...");
        stepRequesting = stepLabel("Requesting file...");
        stepReceiving = stepLabel("Receiving data...");
        stepVerifying = stepLabel("Verifying SHA-256...");
        stepDone = stepLabel("Download complete!");
        markActive(stepConnecting);

        VBox steps = new VBox(6, stepConnecting, stepRequesting, stepReceiving, stepVerifying, stepDone);

        openFile = new Button("Open File");
        openFile.getStyleClass().add("ps-primary");
        openFile.setDisable(true);
        openFile.setOnAction(e -> openResultFile());
        close = new Button("Close");
        close.setOnAction(e -> stage.close());
        HBox buttons = new HBox(10, openFile, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, title, nameLabel, sizeLabel, progressBar, percentLabel, steps, buttons);
        root.getStyleClass().add("ps-card");
        root.setPadding(new Insets(18));

        Scene scene = new Scene(root, 380, 420);
        Theme.track(scene);
        stage.setScene(scene);
    }

    public void show() {
        stage.show();
    }

    /** Safe to call from the transfer thread. */
    public void updateProgress(long received, long total) {
        Platform.runLater(() -> {
            markActive(stepReceiving);
            int pct = total <= 0 ? 100 : (int) Math.min(100, (received * 100) / total);
            progressBar.setProgress(pct / 100.0);
            percentLabel.setText(pct + "%");
            sizeLabel.setText(formatBytes(received) + " / " + formatBytes(total));
        });
    }

    /** Safe to call from the transfer thread. Call once the transfer finishes, success or not. */
    public void onFinished(boolean success, String message, File downloadedFile) {
        this.resultFile = downloadedFile;
        Platform.runLater(() -> {
            markActive(stepVerifying);
            if (success) {
                markDone(stepConnecting);
                markDone(stepRequesting);
                markDone(stepReceiving);
                markDone(stepVerifying);
                markDone(stepDone);
                progressBar.setProgress(1.0);
                percentLabel.setText("100%");
                openFile.setDisable(downloadedFile == null || !downloadedFile.exists());
            } else {
                stepVerifying.setText("\u2717 " + message);
                stepVerifying.getStyleClass().removeAll("ps-check-yes");
                stepVerifying.getStyleClass().add("ps-pill-danger");
            }
        });
    }

    private void openResultFile() {
        if (resultFile == null) return;
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(resultFile);
        } catch (IOException ignored) {
            // Non-fatal: worst case the user opens it manually from the downloads folder.
        }
    }

    private Label stepLabel(String text) {
        Label l = new Label("\u25CB  " + text);
        l.getStyleClass().add("ps-check-no");
        return l;
    }

    private void markActive(Label step) {
        // no special active style beyond "not yet checked"; kept simple/robust
    }

    private void markDone(Label step) {
        String text = step.getText().replaceFirst("^[\u25CB\u2713]\\s+", "");
        step.setText("\u2713  " + text);
        step.getStyleClass().removeAll("ps-check-no");
        if (!step.getStyleClass().contains("ps-check-yes")) step.getStyleClass().add("ps-check-yes");
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1fKB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1fMB", mb);
        return String.format("%.1fGB", mb / 1024.0);
    }
}
