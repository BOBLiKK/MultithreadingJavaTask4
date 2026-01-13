package ehu.java.app;

import ehu.java.client.ClientWorker;
import ehu.java.config.AppConfig;
import ehu.java.config.ConfigLoader;
import ehu.java.pool.ConnectionPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class Application {
    private static final Logger log = LogManager.getLogger(Application.class);

    public static void main(String[] args) throws Exception {

        AppConfig appConfig = ConfigLoader.load("app.properties");

        ConnectionPool pool = new ConnectionPool(appConfig.poolSize());
        ExecutorService executor = Executors.newFixedThreadPool(appConfig.clientsCount());

        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 1; i <= appConfig.clientsCount(); i++) {
            results.add(executor.submit(
                    new ClientWorker(i, pool, appConfig.acquireTimeoutMs(), appConfig.queryTimeMinMs(), appConfig.queryTimeMaxMs())
            ));
        }
        // Stop accepting new tasks
        executor.shutdown();
        // Wait up to 10 seconds for all submitted tasks to complete and the executor to terminate
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        // Count successfully completed client tasks using Future results
        long successfulTasksCount = results.stream().filter(f -> {
            try { return f.get(); }
            catch (Exception e) { return false; }
        }).count();

        log.info("Finished={}. Success={}/{}. Free connections={}",
                finished, successfulTasksCount, appConfig.clientsCount(), pool.freeCount());

        pool.shutdown();
    }
}

