package peershare;

import java.util.Objects;

/** Represents a remote peer discovered on the LAN. */
public class Peer {
    private final String id;
    private final String name;
    private final String host;
    private final int port;
    private volatile long lastSeen;
    private final boolean manual;

    public Peer(String name, String host, int port) {
        this(name, host, port, false);
    }

    public Peer(String name, String host, int port, boolean manual) {
        this.id = host + ":" + port;
        this.name = name;
        this.host = host;
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
        this.manual = manual;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public boolean isManual() { return manual; }

    public void touch() { this.lastSeen = System.currentTimeMillis(); }

    public boolean isStale(long timeoutMillis) {
        if (manual) return false; // manually-added peers are exempt from eviction
        return (System.currentTimeMillis() - lastSeen) > timeoutMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer)) return false;
        return id.equals(((Peer) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return name + " (" + host + ":" + port + ")" + (manual ? " [manual]" : "");
    }
}
