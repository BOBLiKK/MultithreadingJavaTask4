package ehu.java.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ConfigLoader {
    private ConfigLoader() {}

    public static AppConfig load(String classpathResource) throws IOException {
        Properties properties = new Properties();

        //taking class->class loader that loaded that class -> looking for resource on classpath
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Config not found on classpath: " + classpathResource);
            }
            properties.load(in);
        }

        int poolSize = Integer.parseInt(require(properties, "pool.size"));
        int clientsCount = Integer.parseInt(require(properties, "clients.count"));
        long acquireTimeoutMs = Long.parseLong(require(properties, "acquire.timeout.ms"));
        long minMs = Long.parseLong(require(properties, "query.time.ms.min"));
        long maxMs = Long.parseLong(require(properties, "query.time.ms.max"));

        if (poolSize <= 0 || clientsCount <= 0) {
            throw new IllegalArgumentException("pool.size and clients.count must be > 0");
        }
        if (minMs < 0 || maxMs < minMs) {
            throw new IllegalArgumentException("query.time range is invalid");
        }
        return new AppConfig(poolSize, clientsCount, acquireTimeoutMs, minMs, maxMs);
    }

    private static String require(Properties property, String key) {
        String requiredProperty = property.getProperty(key);
        if (requiredProperty == null || requiredProperty.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return requiredProperty.trim();
    }
}
