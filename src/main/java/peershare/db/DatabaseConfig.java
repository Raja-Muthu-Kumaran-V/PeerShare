package peershare.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads MySQL connection settings from db.properties (project root) if it
 * exists, falling back to sensible local-dev defaults that match db/schema.sql.
 */
public final class DatabaseConfig {

    public final String host;
    public final int port;
    public final String database;
    public final String user;
    public final String password;

    private DatabaseConfig(String host, int port, String database, String user, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
    }

    public static DatabaseConfig load() {
        Properties props = new Properties();
        Path configFile = Path.of("db.properties");
        if (Files.exists(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);
            } catch (IOException ignored) {
                // fall through to defaults below
            }
        }
        return new DatabaseConfig(
                props.getProperty("db.host", "127.0.0.1"),
                Integer.parseInt(props.getProperty("db.port", "3306")),
                props.getProperty("db.name", "peershare"),
                props.getProperty("db.user", "peershare"),
                props.getProperty("db.password", "peershare_pw")
        );
    }

    public String jdbcUrl() {
        // MariaDB Connector/J 3.x only accepts the jdbc:mariadb: scheme by default;
        // jdbc:mysql: is rejected with "No suitable driver found" unless
        // permitMysqlScheme is set. Using jdbc:mariadb: directly avoids needing that
        // flag and works against both MariaDB and MySQL servers either way.
        return "jdbc:mariadb://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=3000";
    }
}
