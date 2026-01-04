package ehu.java.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ConfigLoader {
    private ConfigLoader() {}

    public static AppConfig load(String classpathResource) throws IOException {
        Properties p = new Properties();

        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Config not found on classpath: " + classpathResource);
            }
            p.load(in);
        }

        int poolSize = Integer.parseInt(require(p, "pool.size"));
        int clientsCount = Integer.parseInt(require(p, "clients.count"));
        long acquireTimeoutMs = Long.parseLong(require(p, "acquire.timeout.ms"));
        long minMs = Long.parseLong(require(p, "query.time.ms.min"));
        long maxMs = Long.parseLong(require(p, "query.time.ms.max"));

        if (poolSize <= 0 || clientsCount <= 0) {
            throw new IllegalArgumentException("pool.size and clients.count must be > 0");
        }
        if (minMs < 0 || maxMs < minMs) {
            throw new IllegalArgumentException("query.time range is invalid");
        }

        return new AppConfig(poolSize, clientsCount, acquireTimeoutMs, minMs, maxMs);
    }

    private static String require(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return v.trim();
    }
}
