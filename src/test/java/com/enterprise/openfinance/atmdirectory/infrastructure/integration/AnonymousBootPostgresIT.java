package com.enterprise.openfinance.atmdirectory.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.openfinance.atmdirectory.support.PostgresTestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;

/**
 * The service has no authenticated endpoint, so it must start with no identity
 * provider configured at all: build.gradle strips every OIDC_* variable from the
 * test JVM, and nothing in the context may need an issuer or a JWK set.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnonymousBootPostgresIT {

    @BeforeAll
    static void requireDatabase() {
        PostgresTestDatabase.assumeAvailable();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Autowired Environment environment;
    @Autowired MockMvc mvc;

    @Test
    void startsWithoutAnyOidcSettingAndServesTheDirectoryWithoutAToken() throws Exception {
        assertThat(System.getenv().keySet()).noneMatch(name -> name.startsWith("OIDC_"));
        assertThat(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")).isNull();
        assertThat(ClassUtils.isPresent("org.springframework.security.oauth2.jwt.JwtDecoder", null))
            .as("no OAuth2 resource server on the classpath (ADR-0001)").isFalse();
        assertThat(ClassUtils.isPresent("org.springframework.security.web.SecurityFilterChain", null)).isFalse();

        mvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "boot-1"))
            .andExpect(status().isOk());
    }
}
