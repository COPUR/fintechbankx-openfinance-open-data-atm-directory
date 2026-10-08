package com.enterprise.openfinance.atmdirectory.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.openfinance.atmdirectory.support.PostgresTestDatabase.Mode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PostgresTestDatabaseTest {

    @Test
    void runsWheneverADatabaseIsConfigured() {
        assertThat(PostgresTestDatabase.mode(Map.of("TEST_DB_URL", "jdbc:postgresql://db/x"))).isEqualTo(Mode.RUN);
        assertThat(PostgresTestDatabase.mode(Map.of("TEST_DB_URL", "jdbc:postgresql://db/x", "CI", "true"))).isEqualTo(Mode.RUN);
    }

    @Test
    void skipsOnlyOnADeveloperMachine() {
        assertThat(PostgresTestDatabase.mode(Map.of())).isEqualTo(Mode.SKIP);
        assertThat(PostgresTestDatabase.mode(Map.of("TEST_DB_URL", " ", "CI", "false"))).isEqualTo(Mode.SKIP);
    }

    @Test
    void failsInCiWithoutADatabaseInsteadOfSkippingSilently() {
        assertThat(PostgresTestDatabase.mode(Map.of("CI", "true"))).isEqualTo(Mode.FAIL);
        assertThat(PostgresTestDatabase.mode(Map.of("CI", "TRUE", "TEST_DB_URL", ""))).isEqualTo(Mode.FAIL);
        assertThat(PostgresTestDatabase.mode(Map.of("JENKINS_URL", "https://jenkins.example/"))).isEqualTo(Mode.FAIL);
    }
}
