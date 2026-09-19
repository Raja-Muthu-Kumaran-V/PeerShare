-- PeerShare database schema.
-- Run once against a MySQL server: mysql -u root -p < db/schema.sql

CREATE DATABASE IF NOT EXISTS peershare CHARACTER SET utf8mb4;

USE peershare;

CREATE TABLE IF NOT EXISTS transfer_history (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    peer_name     VARCHAR(64)  NOT NULL,
    peer_address  VARCHAR(64)  NOT NULL,
    filename      VARCHAR(255) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    direction     ENUM('SENT', 'RECEIVED') NOT NULL,
    status        ENUM('SUCCESS', 'FAILED', 'HASH_MISMATCH') NOT NULL,
    sha256        CHAR(64)     NULL,
    occurred_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_occurred_at (occurred_at)
) ENGINE=InnoDB;

-- Optional dedicated application user (matches db.properties defaults).
-- Adjust the password before using this in anything beyond a local demo.
-- Grant for both 'localhost' (Unix socket) and '127.0.0.1' (TCP/IP).
-- JDBC always connects over TCP, so the 127.0.0.1 grant is required for
-- reliable connectivity; some MySQL configs (skip-name-resolve) will not
-- map 127.0.0.1 back to localhost.
CREATE USER IF NOT EXISTS 'peershare'@'localhost' IDENTIFIED BY 'peershare_pw';
CREATE USER IF NOT EXISTS 'peershare'@'127.0.0.1' IDENTIFIED BY 'peershare_pw';
GRANT ALL PRIVILEGES ON peershare.* TO 'peershare'@'localhost';
GRANT ALL PRIVILEGES ON peershare.* TO 'peershare'@'127.0.0.1';
FLUSH PRIVILEGES;
