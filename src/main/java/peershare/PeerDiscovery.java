package peershare;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PeerDiscovery {
    public static final int DISCOVERY_PORT = 9876;
    private static final String MAGIC = "PEERSHARE_ANNOUNCE";
    private static final long BROADCAST_INTERVAL_MS = 3000;
    private static final long STALE_TIMEOUT_MS = 9000;

    private final String selfName;
    private final int selfTcpPort;
    private final ConcurrentHashMap<String, Peer> activePeers = new ConcurrentHashMap<>();
    private DatagramSocket socket;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private volatile boolean running = false;

    public PeerDiscovery(String selfName, int selfTcpPort) {
        this.selfName = selfName;
        this.selfTcpPort = selfTcpPort;
    }

    public void start() throws SocketException {
        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(DISCOVERY_PORT));
        socket.setBroadcast(true);
        running = true;

        Thread listener = new Thread(this::listenLoop, "Discovery-Listener");
        listener.setDaemon(true);
        listener.start();

        scheduler.scheduleAtFixedRate(this::broadcastAnnounce, 0, BROADCAST_INTERVAL_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::evictStalePeers, STALE_TIMEOUT_MS, STALE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        scheduler.shutdownNow();
        if (socket != null && !socket.isClosed()) socket.close();
    }

    private void broadcastAnnounce() {
        try {
            String msg = MAGIC + "|" + selfName + "|" + selfTcpPort;
            byte[] data = msg.getBytes();
            for (InetAddress broadcastAddr : listBroadcastAddresses()) {
                socket.send(new DatagramPacket(data, data.length, broadcastAddr, DISCOVERY_PORT));
            }
        } catch (IOException ignored) { }
    }

    private void listenLoop() {
        byte[] buf = new byte[512];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                handleDatagram(new String(packet.getData(), 0, packet.getLength()), packet.getAddress());
            } catch (IOException e) { /* expected on stop() */ }
        }
    }

    private void handleDatagram(String msg, InetAddress fromAddr) {
        String[] parts = msg.split("\\|");
        if (parts.length != 3 || !parts[0].equals(MAGIC)) return;
        String name = parts[1];
        int port;
        try { port = Integer.parseInt(parts[2]); } catch (NumberFormatException e) { return; }
        String host = fromAddr.getHostAddress();
        if (name.equals(selfName) && port == selfTcpPort) return;

        String key = host + ":" + port;
        boolean isLoopback = fromAddr.isLoopbackAddress();
        activePeers.compute(key, (id, existing) -> {
            if (existing == null) return new Peer(name, host, port);
            existing.touch();
            return isLoopback ? existing : new Peer(name, host, port);
        });
    }

    private void evictStalePeers() {
        activePeers.values().removeIf(p -> p.isStale(STALE_TIMEOUT_MS));
    }

    public List<Peer> getActivePeers() { return new ArrayList<>(activePeers.values()); }

    /**
     * Adds a peer directly by host/port, bypassing UDP broadcast discovery.
     * Useful on networks (campus Wi-Fi, VPNs, hotspots) where broadcast is
     * blocked. Manual peers are exempt from stale-eviction.
     */
    public Peer addManualPeer(String name, String host, int port) {
        Peer peer = new Peer(name, host, port, true);
        activePeers.put(peer.getId(), peer);
        return peer;
    }

    /** Removes a peer (manual or discovered) by its id ("host:port"). */
    public void removePeer(String peerId) {
        activePeers.remove(peerId);
    }

    private List<InetAddress> listBroadcastAddresses() {
        List<InetAddress> addrs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getBroadcast() != null) addrs.add(ia.getBroadcast());
                }
            }
        } catch (SocketException ignored) { }
        try {
            addrs.add(InetAddress.getByName("255.255.255.255"));
            addrs.add(InetAddress.getByName("127.255.255.255"));
        } catch (UnknownHostException ignored) { }
        return addrs;
    }
}
