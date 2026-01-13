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

        DatabaseConnection connection = pool.acquire(Duration.ofMillis(200));
        assertNotNull(connection);
        assertEquals(0, pool.freeCount(), "After acquire, freeCount must decrease");

        pool.release(connection);
        assertEquals(1, pool.freeCount(), "After release, freeCount must restore");

        pool.shutdown();
    }

    @Test
    void acquireShouldTimeoutWhenNoFreeConnections() throws Exception {
        ConnectionPool pool = new ConnectionPool(1);

        DatabaseConnection connection = pool.acquire(Duration.ofMillis(200));
        assertNotNull(connection);
        assertEquals(0, pool.freeCount());

        // Now pool is empty(connection taken by first thread), acquiring should timeout
        assertThrows(AcquireTimeoutException.class,
                () -> pool.acquire(Duration.ofMillis(150)));
        pool.release(connection);
        pool.shutdown();
    }

    //check if second thread waits on Condition.await and can acquire after first thread releases connection
    @Test
    void waitingThreadShouldAcquireAfterRelease() throws Exception {
        ConnectionPool pool = new ConnectionPool(1);

        //main thread acquires connection
        DatabaseConnection first = pool.acquire(Duration.ofMillis(200));
        assertNotNull(first);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        CountDownLatch startedWaiting = new CountDownLatch(1); //thread started and reached acquire()
        CountDownLatch acquired = new CountDownLatch(1); //thread acquired connection

        //second thread starts
        Future<DatabaseConnection> future = executorService.submit(() -> {
            startedWaiting.countDown();
            try {
                DatabaseConnection connection = pool.acquire(Duration.ofSeconds(2));
                //condition worked and second thread acquired connection
                acquired.countDown();
                return connection;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        });

        // ensure the second thread started (wait until counter will be decremented to 0 300 ms max)
        assertTrue(startedWaiting.await(300, TimeUnit.MILLISECONDS));

        //main thread returns connection, release should signal and allow the waiting thread to proceed
        pool.release(first);

        //ensure second thread acquired connection
        assertTrue(acquired.await(1, TimeUnit.SECONDS), "Waiting thread must acquire after release");

        //getting result from second thread
        DatabaseConnection second = future.get(1, TimeUnit.SECONDS);
        assertNotNull(second);
        pool.release(second);
        executorService.shutdownNow();
        pool.shutdown();
    }

    @Test
    void concurrentWorkersMustNotUseMoreThanPoolSizeConnectionsAtOnce() throws Exception {
        int poolSize = 3;
        int workers = 12;

        ConnectionPool pool = new ConnectionPool(poolSize);

        ExecutorService executorService = Executors.newFixedThreadPool(workers);

        AtomicInteger inUse = new AtomicInteger(0);
        AtomicInteger maxInUseObserved = new AtomicInteger(0);

        CountDownLatch allStarted = new CountDownLatch(workers);

        Callable<Boolean> job = () -> {
            allStarted.countDown();

            DatabaseConnection connection = null;
            try {
                // wait until all threads reach the latch and starts all together
                allStarted.await(1, TimeUnit.SECONDS);

                connection = pool.acquire(Duration.ofSeconds(2));

                int now = inUse.incrementAndGet();
                //checking the max in use during all the time of running the test
                maxInUseObserved.updateAndGet(prev -> Math.max(prev, now));

                // keep it for a bit so overlap happens
                TimeUnit.MILLISECONDS.sleep(150);

                return true;
            } finally {
                if (connection != null) {
                    inUse.decrementAndGet();
                    pool.release(connection);
                }
            }
        };

        Future<?>[] futures = new Future[workers];
        for (int i = 0; i < workers; i++) {
            futures[i] = executorService.submit(job);
        }

        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }

        assertTrue(maxInUseObserved.get() <= poolSize,
                "Max simultaneous acquired connections must be <= pool size");

        executorService.shutdownNow();
        pool.shutdown();
    }
}
