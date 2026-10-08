package com.enterprise.openfinance.atmdirectory.support;

import org.junit.jupiter.api.Assumptions;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * PostgreSQL for integration tests, taken from TEST_DB_URL / TEST_DB_USERNAME /
 * TEST_DB_PASSWORD (a service container in required-gates.yml). Without
 * TEST_DB_URL the tests are skipped, not failed.
 */
public final class PostgresTestDatabase {

    private PostgresTestDatabase() {
    }

    /** Call from a static @BeforeAll. */
    public static void assumeAvailable() {
        Assumptions.assumeTrue(isConfigured(), "Set TEST_DB_URL to run PostgreSQL integration tests");
    }

    public static boolean isConfigured() {
        String url = System.getenv("TEST_DB_URL");
        return url != null && !url.isBlank();
    }

    public static void register(DynamicPropertyRegistry registry) {
        if (!isConfigured()) {
            return;
        }
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", PostgresTestDatabase::username);
        registry.add("spring.datasource.password", PostgresTestDatabase::password);
    }

    public static String username() {
        return env("TEST_DB_USERNAME", "atm_test");
    }

    public static String password() {
        return env("TEST_DB_PASSWORD", "atm_test");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
