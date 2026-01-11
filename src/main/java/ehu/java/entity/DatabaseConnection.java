package ehu.java.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DatabaseConnection {
    private static final Logger log = LogManager.getLogger(DatabaseConnection.class);

    private final int id;
    private boolean isConnectionOpened = true;

    public DatabaseConnection(int id) {
        this.id = id;
        log.info("Connection #{} created", id);
    }

    public int getId() {
        return id;
    }

    public boolean isConnectionOpened() {
        return isConnectionOpened;
    }

    public void executeQuery(String string) {
        if (!isConnectionOpened) {
            throw new IllegalStateException("Connection #" + id + " is closed");
        }
        log.info("Connection #{} executing query: {}", id, string);
    }

    public void close() {
        isConnectionOpened = false;
        log.info("Connection #{} closed", id);
    }
}
