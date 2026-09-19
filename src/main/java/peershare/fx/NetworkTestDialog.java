package peershare.fx;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import peershare.NetworkUtil;

import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;

/**
 * "Test My Network" dialog. Runs the same real checks NetworkUtil.buildReport()
 * does (interface enumeration, private-LAN classification, UDP loopback probe)
 * but renders them as a checklist instead of a text dump.
 * There is no separate fabricated "broadcast
 * test" line - PeerShare doesn't have a standalone broadcast self-test, only
 * the loopback UDP probe, so that's what's shown here (accurately).
 */
public final class NetworkTestDialog {
    private NetworkTestDialog() {}

    public static void show(Window owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Network Test");

        Label title = new Label("Network Test");
        title.getStyleClass().add("ps-card-title");
        Label subtitle = new Label("Check your network configuration and connectivity.");
        subtitle.getStyleClass().add("ps-subtle");

        VBox checklist = new VBox(10);

        List<InetAddress> addrs;
        boolean interfacesOk;
        try {
            addrs = NetworkUtil.listNonLoopbackIPv4Addresses();
            interfacesOk = !addrs.isEmpty();
        } catch (SocketException e) {
            addrs = List.of();
            interfacesOk = false;
        }
        checklist.getChildren().add(checkRow(interfacesOk,
                "Network interfaces detected",
                interfacesOk ? addrs.size() + " address(es) found" : "None found"));

        boolean privateIpOk = addrs.stream().anyMatch(NetworkUtil::isPrivateLan);
        String privateIpValue = addrs.stream().filter(NetworkUtil::isPrivateLan)
                .map(InetAddress::getHostAddress).findFirst().orElse("None");
        checklist.getChildren().add(checkRow(privateIpOk, "Private IP detected", privateIpValue));

        boolean udpOk = NetworkUtil.probeLoopbackUdp();
        checklist.getChildren().add(checkRow(udpOk, "UDP loopback test", udpOk ? "Success" : "Failed"));

        NetworkUtil.NetworkClass cls = NetworkUtil.classify();
        boolean overallGood = cls == NetworkUtil.NetworkClass.PRIVATE_LAN && udpOk;

        Label banner = new Label((overallGood ? "Network looks good!\n" : "Network may have issues.\n") + cls.description);
        banner.setWrapText(true);
        banner.getStyleClass().add(overallGood ? "ps-pill-success" : "ps-pill-warning");
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setPadding(new Insets(10));

        Button close = new Button("Close");
        close.setOnAction(e -> dialog.close());
        HBox buttons = new HBox(close);
        buttons.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        VBox root = new VBox(14, title, subtitle, checklist, banner, buttons);
        root.getStyleClass().add("ps-card");
        root.setPadding(new Insets(18));

        Scene scene = new Scene(root, 420, 380);
        Theme.track(scene);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private static HBox checkRow(boolean ok, String label, String value) {
        Label icon = new Label(ok ? "\u2713" : "\u2717");
        icon.getStyleClass().add(ok ? "ps-check-yes" : "ps-check-no");
        Label l = new Label(label);
        l.setPrefWidth(200);
        Label v = new Label(value);
        v.getStyleClass().add("ps-subtle");
        HBox row = new HBox(10, icon, l, v);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }
}
