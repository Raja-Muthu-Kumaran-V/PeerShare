package peershare.fx;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;

/** "Add Peer Manually" dialog: name, IP/host, TCP port. */
public final class AddPeerDialog {
    private AddPeerDialog() {}

    public record Result(String name, String host, int port) {}

    public static Optional<Result> show(Window owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Add Peer Manually");

        Label title = new Label("Add Peer Manually");
        title.getStyleClass().add("ps-card-title");
        Label subtitle = new Label("Enter the peer details to connect manually.");
        subtitle.getStyleClass().add("ps-subtle");

        TextField nameField = new TextField();
        TextField hostField = new TextField();
        TextField portField = new TextField();
        Label error = new Label(" ");
        error.getStyleClass().add("ps-pill-danger");

        GridPane form = new GridPane();
        form.setVgap(10);
        form.setHgap(8);
        form.addRow(0, labeled("Peer Name", nameField));
        form.addRow(1, labeled("IP Address / Host", hostField));
        form.addRow(2, labeled("TCP Port", portField));

        final Result[] outcome = new Result[1];

        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> dialog.close());
        Button add = new Button("Add Peer");
        add.getStyleClass().add("ps-primary");
        add.setOnAction(e -> {
            String name = nameField.getText().trim();
            String host = hostField.getText().trim();
            if (name.isEmpty() || host.isEmpty()) {
                error.setText("Peer name and host are both required.");
                return;
            }
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ex) {
                error.setText("TCP port must be a whole number.");
                return;
            }
            if (port <= 0 || port > 65535) {
                error.setText("TCP port must be between 1 and 65535.");
                return;
            }
            outcome[0] = new Result(name, host, port);
            dialog.close();
        });
        javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(10, cancel, add);
        buttons.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        VBox root = new VBox(14, title, subtitle, form, error, buttons);
        root.getStyleClass().add("ps-card");
        root.setPadding(new Insets(18));

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 380, 320);
        Theme.track(scene);
        dialog.setScene(scene);
        dialog.showAndWait();

        return Optional.ofNullable(outcome[0]);
    }

    private static VBox labeled(String label, TextField field) {
        Label l = new Label(label);
        l.getStyleClass().add("ps-field-label");
        return new VBox(4, l, field);
    }
}
