package ehu.java.pool;

import ehu.java.entity.DatabaseConnection;
import ehu.java.exception.AcquireTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class ConnectionPool {
    private static final Logger log = LogManager.getLogger(ConnectionPool.class);

    // MONITOR: lock + condition
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition hasFreeConnection = lock.newCondition();

    private final Deque<DatabaseConnection> freeConnections = new ArrayDeque<>();
    private boolean isPoolClosed = false;

    public ConnectionPool(int poolSize) {
        for (int i = 1; i <= poolSize; i++) {
            freeConnections.addLast(new DatabaseConnection(i));
        }
        log.info("ConnectionPool initialized. size={}", poolSize);
    }

    /**
     * Acquire connection with timeout.
     * If no free connections -> await on Condition until signaled or timeout.
     */
    public DatabaseConnection acquire(Duration timeout)
            throws InterruptedException, AcquireTimeoutException {
        long nanos = timeout.toNanos();
        lock.lock();
        try {
            if (isPoolClosed) {
                throw new IllegalStateException("Pool is shutdown");
            }
            while (freeConnections.isEmpty() && !isPoolClosed) {
                if (nanos <= 0L) {
                    throw new AcquireTimeoutException("Timeout: no free connection available");
                }
                nanos = hasFreeConnection.awaitNanos(nanos);
            }
            DatabaseConnection c = freeConnections.removeFirst();
            log.info("{} acquired connection #{}", Thread.currentThread().getName(), c.getId());
            return c;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Release connection back to pool and signal waiting thread.
     */
    public void release(DatabaseConnection connection) {
        if (connection == null) return;

        lock.lock();
        try {
            if (isPoolClosed) {
                connection.close();
                return;
            }

            if (!connection.isConnectionOpened()) {
                log.warn("Connection #{} is closed, not returning to pool", connection.getId());
                return;
            }

            freeConnections.addLast(connection);
            hasFreeConnection.signal();
        } finally {
            lock.unlock();
        }
    }


    public int freeCount() {
        lock.lock();
        try {
            return freeConnections.size();
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        lock.lock();
        try {
            isPoolClosed = true;
            while (!freeConnections.isEmpty()) {
                freeConnections.removeFirst().close();
            }
            // Wake up all in case someone is waiting forever during shutdown
            hasFreeConnection.signalAll();
            log.info("ConnectionPool shutdown completed");
        } finally {
            lock.unlock();
        }
    }
}
