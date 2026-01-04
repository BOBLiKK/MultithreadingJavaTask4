import ehu.java.entity.DatabaseConnection;
import ehu.java.exception.AcquireTimeoutException;
import ehu.java.pool.ConnectionPool;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionPoolTest {

    @Test
    void acquireShouldReturnConnectionImmediatelyWhenFreeExists() throws Exception {
        ConnectionPool pool = new ConnectionPool(1);

        DatabaseConnection c = pool.acquire(Duration.ofMillis(200));
        assertNotNull(c);
        assertEquals(0, pool.freeCount(), "After acquire, freeCount must decrease");

        pool.release(c);
        assertEquals(1, pool.freeCount(), "After release, freeCount must restore");

        pool.shutdown();
    }

    @Test
    void acquireShouldTimeoutWhenNoFreeConnections() throws Exception {
        ConnectionPool pool = new ConnectionPool(1);

        DatabaseConnection c1 = pool.acquire(Duration.ofMillis(200));
        assertNotNull(c1);
        assertEquals(0, pool.freeCount());

        // Now pool is empty, acquiring should timeout
        assertThrows(AcquireTimeoutException.class,
                () -> pool.acquire(Duration.ofMillis(150)));

        pool.release(c1);
        pool.shutdown();
    }

    @Test
    void waitingThreadShouldAcquireAfterRelease() throws Exception {
        ConnectionPool pool = new ConnectionPool(1);

        DatabaseConnection first = pool.acquire(Duration.ofMillis(200));
        assertNotNull(first);

        ExecutorService exec = Executors.newSingleThreadExecutor();
        CountDownLatch startedWaiting = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(1);

        Future<DatabaseConnection> future = exec.submit(() -> {
            startedWaiting.countDown();
            try {
                DatabaseConnection c = pool.acquire(Duration.ofSeconds(2));
                acquired.countDown();
                return c;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        });

        // ensure the second thread started (and will likely wait)
        assertTrue(startedWaiting.await(300, TimeUnit.MILLISECONDS));

        // release should signal and allow the waiting thread to proceed
        pool.release(first);

        assertTrue(acquired.await(1, TimeUnit.SECONDS), "Waiting thread must acquire after release");

        DatabaseConnection second = future.get(1, TimeUnit.SECONDS);
        assertNotNull(second);

        pool.release(second);

        exec.shutdownNow();
        pool.shutdown();
    }

    @Test
    void concurrentWorkersMustNotUseMoreThanPoolSizeConnectionsAtOnce() throws Exception {
        int poolSize = 3;
        int workers = 12;

        ConnectionPool pool = new ConnectionPool(poolSize);

        ExecutorService exec = Executors.newFixedThreadPool(workers);

        AtomicInteger inUse = new AtomicInteger(0);
        AtomicInteger maxInUseObserved = new AtomicInteger(0);

        CountDownLatch allStarted = new CountDownLatch(workers);

        Callable<Boolean> job = () -> {
            allStarted.countDown();

            DatabaseConnection c = null;
            try {
                // wait until most threads are ready -> creates contention
                allStarted.await(1, TimeUnit.SECONDS);

                c = pool.acquire(Duration.ofSeconds(2));

                int now = inUse.incrementAndGet();
                maxInUseObserved.updateAndGet(prev -> Math.max(prev, now));

                // keep it for a bit so overlap happens
                TimeUnit.MILLISECONDS.sleep(150);

                return true;
            } finally {
                if (c != null) {
                    inUse.decrementAndGet();
                    pool.release(c);
                }
            }
        };

        Future<?>[] futures = new Future[workers];
        for (int i = 0; i < workers; i++) {
            futures[i] = exec.submit(job);
        }

        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }

        assertTrue(maxInUseObserved.get() <= poolSize,
                "Max simultaneous acquired connections must be <= pool size");

        exec.shutdownNow();
        pool.shutdown();
    }
}
