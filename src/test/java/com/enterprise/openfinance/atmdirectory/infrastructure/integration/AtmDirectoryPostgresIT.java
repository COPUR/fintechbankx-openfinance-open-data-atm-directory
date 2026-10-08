package com.enterprise.openfinance.atmdirectory.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.model.GeoBoundingBox;
import com.enterprise.openfinance.atmdirectory.domain.port.out.AtmDirectoryPort;
import com.enterprise.openfinance.atmdirectory.support.PostgresTestDatabase;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the whole service against PostgreSQL: Flyway builds sc_of_atm_directory
 * and loads the sample seed, Hibernate validates the entity against it, and the
 * public endpoint is served from the database.
 */
@SpringBootTest(properties = "atm-directory.seed.enabled=true")
@AutoConfigureMockMvc
class AtmDirectoryPostgresIT {

    private static final String SCHEMA = "sc_of_atm_directory";

    @BeforeAll
    static void requireDatabase() {
        PostgresTestDatabase.assumeAvailable();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AtmDirectoryPort directory;

    @Test
    void flywayCreatesOnlyTheTableThisServiceOwnsWithItsIndexes() {
        List<String> tables = jdbc.queryForList("""
            select table_name from information_schema.tables
            where table_schema = ? and table_name <> 'flyway_schema_history' order by table_name
            """, String.class, SCHEMA);
        List<String> indexes = jdbc.queryForList(
            "select indexname from pg_indexes where schemaname = ? and tablename = 'atm' order by indexname",
            String.class, SCHEMA);
        List<String> outbox = jdbc.queryForList(
            "select table_name from information_schema.tables where table_name like 'outbox%' and table_schema = ?",
            String.class, SCHEMA);

        assertThat(tables).containsExactly("atm");
        assertThat(indexes).containsExactly("ix_atm_country_city", "ix_atm_location", "ix_atm_status", "pk_atm");
        assertThat(outbox).as("no outbox until a write use case exists (ADR-0001)").isEmpty();
    }

    @Test
    void seedLoadsTheThreeSampleAtms() {
        assertThat(directory.findAll()).extracting(AtmLocation::atmId)
            .containsExactly("SAMPLE-001", "SAMPLE-002", "SAMPLE-003");
        AtmLocation downtown = directory.findAll().getFirst();
        assertThat(downtown.services()).containsExactly("CashWithdrawal", "CashDeposit");
        assertThat(downtown.currency()).isEqualTo("AED");
        assertThat(downtown.updatedAt()).hasToString("2026-03-01T00:00:00Z");
    }

    @Test
    void boundingBoxQueryUsesInclusiveEdgesAndExcludesFarAtms() {
        // Edges exactly on Marina (25.08, 55.14) and downtown (25.2048, 55.2708).
        assertThat(directory.findWithin(new GeoBoundingBox(25.08, 25.2048, 55.14, 55.2708)))
            .extracting(AtmLocation::atmId).containsExactly("SAMPLE-001", "SAMPLE-002");
        assertThat(directory.findWithin(new GeoBoundingBox(-10, 10, -10, 10))).isEmpty();
    }

    @Test
    void radiusSearchOverHttpIsServedFromPostgres() throws Exception {
        mvc.perform(get("/open-finance/v1/atms?lat=25.2048&long=55.2708&radius=25").header("X-FAPI-Interaction-ID", "it-pg-1"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andExpect(jsonPath("$.Meta.TotalRecords").value(2))
            .andExpect(jsonPath("$.Data.ATM[*].AtmId").value(contains("SAMPLE-001", "SAMPLE-002")));

        mvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-pg-2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Meta.TotalRecords").value(3));
    }

    @Test
    void schemaRejectsRowsThatBreakTheDirectoryRules() {
        JdbcTemplate writable = writableJdbc();
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class, () -> writable.update("""
            insert into sc_of_atm_directory.atm (atm_id, name, status, latitude, longitude, address_line, city,
              country_code, accessibility, services, currency)
            values ('ATM-BAD', 'Bad', 'InService', 95, 55, 'Road', 'Dubai', 'AE', 'Standard', ARRAY['CashWithdrawal'], 'AED')
            """));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class, () -> writable.update("""
            insert into sc_of_atm_directory.atm (atm_id, name, status, latitude, longitude, address_line, city,
              country_code, accessibility, services, currency)
            values ('ATM-BAD', 'Bad', 'InService', 25, 55, 'Road', 'Dubai', 'AE', 'Standard', ARRAY[]::text[], 'AED')
            """));
    }

    @Test
    void applicationConnectionsAreReadOnly() {
        assertThatThrownBy(() -> jdbc.update("delete from sc_of_atm_directory.atm where atm_id = 'SAMPLE-003'"))
            .hasMessageContaining("read-only");
        assertThat(directory.findAll()).hasSize(3);
    }

    @Test
    void forwardedHostIsNotReflectedThroughTheProductionFilterChain() throws Exception {
        String body = mvc.perform(get("/open-finance/v1/atms?lat=25.2048&long=55.2708")
                .header("X-FAPI-Interaction-ID", "it-pg-3")
                .header("X-Forwarded-Host", "evil.example"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andExpect(jsonPath("$.Links.Self").value("/open-finance/v1/atms?lat=25.2048&long=55.2708"))
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("evil.example");
    }

    private static JdbcTemplate writableJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(System.getenv("TEST_DB_URL"),
            PostgresTestDatabase.username(), PostgresTestDatabase.password()));
    }
}
