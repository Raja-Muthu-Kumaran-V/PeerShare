package peershare;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Classifies the local network so the UI can warn the user when UDP
 * broadcast discovery is likely to be blocked (campus Wi-Fi, VPNs,
 * hotspots, etc), and offers a quick loopback self-test.
 */
public class NetworkUtil {

    public enum NetworkClass {
        PRIVATE_LAN("On a private LAN - broadcast discovery should work."),
        LIKELY_RESTRICTED("Network looks restricted (VPN/hotspot/campus-style) - broadcast discovery may be blocked. Use \"Add Peer Manually\" if peers don't show up."),
        NONE("No usable network interface found."),
        UNKNOWN("Could not determine network type.");

        public final String description;
        NetworkClass(String description) { this.description = description; }
    }

    private NetworkUtil() { }

    /** Best-effort classification of the machine's current network situation. */
    public static NetworkClass classify() {
        try {
            List<InetAddress> addrs = listNonLoopbackIPv4Addresses();
            if (addrs.isEmpty()) return NetworkClass.NONE;
            for (InetAddress addr : addrs) {
                if (isPrivateLan(addr)) return NetworkClass.PRIVATE_LAN;
            }
            return NetworkClass.LIKELY_RESTRICTED;
        } catch (SocketException e) {
            return NetworkClass.UNKNOWN;
        }
    }

    /** RFC1918 private-address check (10/8, 172.16/12, 192.168/16). */
    public static boolean isPrivateLan(InetAddress addr) {
        if (!(addr instanceof Inet4Address)) return false;
        byte[] b = addr.getAddress();
        int first = b[0] & 0xFF;
        int second = b[1] & 0xFF;
        if (first == 10) return true;
        if (first == 172 && second >= 16 && second <= 31) return true;
        if (first == 192 && second == 168) return true;
        return false;
    }

    public static List<InetAddress> listNonLoopbackIPv4Addresses() throws SocketException {
        List<InetAddress> result = new ArrayList<>();
        Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
        while (nis.hasMoreElements()) {
            NetworkInterface ni = nis.nextElement();
            if (ni.isLoopback() || !ni.isUp()) continue;
            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (addr instanceof Inet4Address) result.add(addr);
            }
        }
        return result;
    }

    /**
     * Sends a UDP packet to itself over loopback and confirms it comes back,
     * as a quick sanity check that UDP sockets aren't being blocked outright
     * (e.g. by an overzealous local firewall or AV product).
     */
    public static boolean probeLoopbackUdp() {
        final String probeMsg = "PEERSHARE_LOOPBACK_PROBE";
        try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            socket.setSoTimeout(1000);
            int port = socket.getLocalPort();
            byte[] data = probeMsg.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getLoopbackAddress(), port);
            socket.send(sendPacket);

            byte[] buf = new byte[64];
            DatagramPacket recvPacket = new DatagramPacket(buf, buf.length);
            socket.receive(recvPacket);
            String received = new String(recvPacket.getData(), 0, recvPacket.getLength());
            return probeMsg.equals(received);
        } catch (IOException e) {
            return false;
        }
    }

    /** Human-readable summary for the "Test My Network" report dialog. */
    public static String buildReport() {
        StringBuilder sb = new StringBuilder();
        NetworkClass cls = classify();
        sb.append("Network assessment: ").append(cls).append("\n");
        sb.append(cls.description).append("\n\n");

        try {
            List<InetAddress> addrs = listNonLoopbackIPv4Addresses();
            if (addrs.isEmpty()) {
                sb.append("No active non-loopback IPv4 interfaces detected.\n");
            } else {
                sb.append("Detected addresses:\n");
                for (InetAddress a : addrs) {
                    sb.append("  - ").append(a.getHostAddress())
                      .append(isPrivateLan(a) ? " (private LAN)" : " (not RFC1918 private)")
                      .append("\n");
                }
            }
        } catch (SocketException e) {
            sb.append("Could not enumerate network interfaces: ").append(e.getMessage()).append("\n");
        }

        sb.append("\nLoopback UDP self-probe: ");
        sb.append(probeLoopbackUdp() ? "PASSED (UDP sockets are not blocked locally)"
                                      : "FAILED (a local firewall/AV may be blocking UDP)");
        return sb.toString();
    }
}
