package com.example.marketflow.marketplace;

import java.util.UUID;

final class PostgresTestSupport {
    private PostgresTestSupport() {}

    static String schema(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    static String url(String schema) {
        String base = System.getenv("MARKETFLOW_TEST_POSTGRES_URL");
        return base + (base.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    static String username() {
        return System.getenv().getOrDefault("MARKETFLOW_TEST_POSTGRES_USER", "marketflow_test");
    }

    static String password() {
        return System.getenv().getOrDefault("MARKETFLOW_TEST_POSTGRES_PASSWORD", "");
    }
}
