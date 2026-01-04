package ehu.java.config;

public record AppConfig(
        int poolSize,
        int clientsCount,
        long acquireTimeoutMs,
        long queryTimeMinMs,
        long queryTimeMaxMs
) {}
