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

        int threads = Math.min(appConfig.clientsCount(), Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 1; i <= appConfig.clientsCount(); i++) {
            results.add(executor.submit(
                    new ClientWorker(i, pool, appConfig.acquireTimeoutMs(), appConfig.queryTimeMinMs(), appConfig.queryTimeMaxMs())
            ));
        }
        executor.shutdown();
        boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);

        long successfulTasksCount = results.stream().filter(f -> {
            try { return f.get(); }
            catch (Exception e) { return false; }
        }).count();

        log.info("Finished={}. Success={}/{}. Free connections={}",
                finished, successfulTasksCount, appConfig.clientsCount(), pool.freeCount());

        pool.shutdown();
    }
}

