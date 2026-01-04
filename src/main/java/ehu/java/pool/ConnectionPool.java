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

    private final Deque<DatabaseConnection> free = new ArrayDeque<>();

    public ConnectionPool(int poolSize) {
        for (int i = 1; i <= poolSize; i++) {
            free.addLast(new DatabaseConnection(i));
        }
        log.info("ConnectionPool initialized. size={}", poolSize);
    }

    /**
     * Acquire connection with timeout.
     * If no free connections -> await on Condition until signaled or timeout.
     */
    public DatabaseConnection acquire(Duration timeout) throws InterruptedException, AcquireTimeoutException {
        long nanos = timeout.toNanos();
        lock.lock();
        try {
            while (free.isEmpty()) {
                if (nanos <= 0L) {
                    throw new AcquireTimeoutException("Timeout: no free connection available");
                }
                log.info("No free connections. {} is waiting...", Thread.currentThread().getName());
                nanos = hasFreeConnection.awaitNanos(nanos);
            }

            DatabaseConnection c = free.removeFirst();
            log.info("{} acquired connection #{}", Thread.currentThread().getName(), c.getId());
            return c;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Release connection back to pool and signal waiting thread.
     */
    public void release(DatabaseConnection c) {
        if (c == null) return;

        lock.lock();
        try {
            if (!c.isOpen()) {
                log.warn("Connection #{} is closed, not returning to pool", c.getId());
                return;
            }

            free.addLast(c);
            log.info("{} released connection #{}", Thread.currentThread().getName(), c.getId());

            // Notify one waiting thread that a resource became available
            hasFreeConnection.signal();
        } finally {
            lock.unlock();
        }
    }

    public int freeCount() {
        lock.lock();
        try {
            return free.size();
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        lock.lock();
        try {
            while (!free.isEmpty()) {
                free.removeFirst().close();
            }
            // Wake up all in case someone is waiting forever during shutdown
            hasFreeConnection.signalAll();
            log.info("ConnectionPool shutdown completed");
        } finally {
            lock.unlock();
        }
    }
}
