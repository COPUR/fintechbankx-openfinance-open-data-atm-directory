package com.enterprise.openfinance.atmdirectory.support;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * PostgreSQL for integration tests, taken from TEST_DB_URL / TEST_DB_USERNAME /
 * TEST_DB_PASSWORD (a service container in required-gates.yml). Without
 * TEST_DB_URL they are skipped on a developer machine, but fail in CI (CI=true,
 * as GitHub Actions and GitLab set it, or JENKINS_URL set) so a pipeline cannot
 * pass with the database tests silently skipped.
 */
public final class PostgresTestDatabase {

    private PostgresTestDatabase() {
    }

    enum Mode { RUN, SKIP, FAIL }

    /** Call from a static @BeforeAll. */
    public static void assumeAvailable() {
        switch (mode(System.getenv())) {
            case RUN -> { }
            case FAIL -> Assertions.fail("TEST_DB_URL is not set in CI (CI=true or JENKINS_URL): "
                + "PostgreSQL integration tests must run, not be skipped. Provide TEST_DB_URL, "
                + "TEST_DB_USERNAME and TEST_DB_PASSWORD.");
            case SKIP -> Assumptions.abort("Set TEST_DB_URL to run PostgreSQL integration tests");
        }
    }

    static Mode mode(Map<String, String> env) {
        if (!blank(env.get("TEST_DB_URL"))) {
            return Mode.RUN;
        }
        boolean ci = "true".equalsIgnoreCase(env.get("CI")) || !blank(env.get("JENKINS_URL"));
        return ci ? Mode.FAIL : Mode.SKIP;
    }

    public static boolean isConfigured() {
        return mode(System.getenv()) == Mode.RUN;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
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
