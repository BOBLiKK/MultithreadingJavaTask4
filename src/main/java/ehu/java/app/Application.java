package ehu.java.app;

import ehu.java.client.ClientWorker;
import ehu.java.config.AppConfig;
import ehu.java.config.ConfigLoader;
import ehu.java.pool.ConnectionPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class Application {
    private static final Logger log = LogManager.getLogger(Application.class);

    public static void main(String[] args) throws Exception {
        // Config file located in project-root/config/app.properties
        AppConfig cfg = ConfigLoader.load("app.properties");


        ConnectionPool pool = new ConnectionPool(cfg.poolSize());

        int threads = Math.min(cfg.clientsCount(), Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 1; i <= cfg.clientsCount(); i++) {
            results.add(executor.submit(
                    new ClientWorker(i, pool, cfg.acquireTimeoutMs(), cfg.queryTimeMinMs(), cfg.queryTimeMaxMs())
            ));
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);

        long ok = results.stream().filter(f -> {
            try { return f.get(); }
            catch (Exception e) { return false; }
        }).count();

        log.info("Finished={}. Success={}/{}. Free connections={}",
                finished, ok, cfg.clientsCount(), pool.freeCount());

        pool.shutdown();
    }
}

