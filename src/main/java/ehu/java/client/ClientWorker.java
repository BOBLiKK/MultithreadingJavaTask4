package ehu.java.client;

import ehu.java.pool.ConnectionPool;
import ehu.java.entity.DatabaseConnection;
import ehu.java.exception.AcquireTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class ClientWorker implements Callable<Boolean> {
    private static final Logger log = LogManager.getLogger(ClientWorker.class);

    private final int clientId;
    private final ConnectionPool pool;
    private final long acquireTimeoutMs;
    private final long queryMinMs;
    private final long queryMaxMs;

    public ClientWorker(int clientId,
                        ConnectionPool pool,
                        long acquireTimeoutMs,
                        long queryMinMs,
                        long queryMaxMs) {
        this.clientId = clientId;
        this.pool = pool;
        this.acquireTimeoutMs = acquireTimeoutMs;
        this.queryMinMs = queryMinMs;
        this.queryMaxMs = queryMaxMs;
    }

    @Override
    public Boolean call() throws Exception {
        Thread.currentThread().setName("client-" + clientId);

        DatabaseConnection connection = null;
        try {
            connection = pool.acquire(Duration.ofMillis(acquireTimeoutMs));

            // Each thread uses its own random generator (ThreadLocalRandom) to avoid contention
            long workMs = ThreadLocalRandom.current().nextLong(queryMinMs, queryMaxMs + 1);
            connection.executeQuery("SELECT * FROM orders WHERE client_id = " + clientId);
            TimeUnit.MILLISECONDS.sleep(workMs);

            log.info("Client {} finished work using connection #{}", clientId, connection.getId());
            return true;

        } catch (AcquireTimeoutException ex) {
            log.error("Client {} failed to acquire connection: {}", clientId, ex.getMessage());
            return false;

        } finally {
            pool.release(connection);
        }
    }
}

