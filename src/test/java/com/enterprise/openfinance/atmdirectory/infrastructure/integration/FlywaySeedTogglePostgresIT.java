package com.enterprise.openfinance.atmdirectory.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.openfinance.atmdirectory.infrastructure.config.FlywaySeedConfiguration;
import com.enterprise.openfinance.atmdirectory.support.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The seed toggle in a real environment: a dev database is migrated once with
 * ATM_DIRECTORY_SEED_ENABLED=true (R__seed_sample_atms applied), then the toggle is
 * switched off. The next start runs Flyway without classpath:db/seed, so the applied
 * repeatable seed is no longer resolved; application.yml (spring.flyway
 * ignore-migration-patterns "*:missing") must keep validation passing.
 *
 * Runs Spring Boot's own Flyway auto-configuration with application.yml and
 * FlywaySeedConfiguration, in a schema of its own so the other ITs are untouched.
 */
class FlywaySeedTogglePostgresIT {

    private static final String SCHEMA = "sc_of_atm_directory_seed_toggle_it";
    private static final String SEED_SCRIPT = "R__seed_sample_atms.sql";

    @BeforeAll
    static void requireDatabase() {
        PostgresTestDatabase.assumeAvailable();
    }

    private final ApplicationContextRunner flywayOnly = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
        .withUserConfiguration(FlywaySeedConfiguration.class)
        .withPropertyValues(
            "spring.datasource.url=" + System.getenv("TEST_DB_URL"),
            "spring.datasource.username=" + PostgresTestDatabase.username(),
            "spring.datasource.password=" + PostgresTestDatabase.password(),
            "spring.datasource.hikari.schema=" + SCHEMA,
            "spring.flyway.schemas=" + SCHEMA,
            "spring.flyway.default-schema=" + SCHEMA);

    @BeforeEach
    @AfterEach
    void dropSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                System.getenv("TEST_DB_URL"), PostgresTestDatabase.username(), PostgresTestDatabase.password());
             Statement statement = connection.createStatement()) {
            statement.execute("drop schema if exists " + SCHEMA + " cascade");
        }
    }

    @Test
    void switchingTheSeedOffAfterItWasAppliedStillValidatesAndStarts() {
        flywayOnly.withPropertyValues("atm-directory.seed.enabled=true").run(seeded -> {
            assertThat(seeded).hasNotFailed();
            assertThat(appliedScripts(seeded.getBean(Flyway.class))).contains(SEED_SCRIPT);
        });

        flywayOnly.withPropertyValues("atm-directory.seed.enabled=false").run(unseeded -> {
            // Spring Boot migrates on start with validate-on-migrate, so a missing
            // seed would already fail the context here.
            assertThat(unseeded).hasNotFailed();
            ValidateResult validation = unseeded.getBean(Flyway.class).validateWithResult();
            assertThat(validation.validationSuccessful)
                .as("Flyway validate with db/seed removed: %s", validation.getAllErrorMessages())
                .isTrue();
            // The applied seed stays recorded and its rows stay; only the location is gone.
            assertThat(appliedScripts(unseeded.getBean(Flyway.class))).contains(SEED_SCRIPT);
            assertThat(query("select atm_id from " + SCHEMA + ".atm order by atm_id"))
                .containsExactly("SAMPLE-001", "SAMPLE-002", "SAMPLE-003");
        });
    }

    private static List<String> appliedScripts(Flyway flyway) throws SQLException {
        return query("select script from " + flyway.getConfiguration().getDefaultSchema()
            + ".flyway_schema_history where success order by installed_rank");
    }

    private static List<String> query(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                System.getenv("TEST_DB_URL"), PostgresTestDatabase.username(), PostgresTestDatabase.password());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }
}
