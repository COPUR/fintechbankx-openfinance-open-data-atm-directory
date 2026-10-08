package com.enterprise.openfinance.atmdirectory.infrastructure.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AtmDirectoryOpenApiContractTest {

    @Test
    void openApiShouldDefinePublicAtmEndpoint() throws IOException {
        String yaml = Files.readString(Path.of("api/openapi/atm-directory-service.yaml"));

        assertThat(yaml).contains("/atms:");
        assertThat(yaml).contains("operationId: listAtms");
        assertThat(yaml).contains("security: []");
        assertThat(yaml).contains("X-FAPI-Interaction-ID");
        assertThat(yaml).contains("Cache-Control:", "Currency:", "'503':", "Retry-After:");
    }

    @Test
    void openApiDocumentsTheGatewayRateLimitOnTheAnonymousRoute() throws IOException {
        String yaml = Files.readString(Path.of("api/openapi/atm-directory-service.yaml"));
        String tooMany = yaml.substring(yaml.indexOf("'429':"), yaml.indexOf("'503':"));

        assertThat(tooMany).contains("API gateway", "x-fbx-rate-limited:", "- 'true'", "retry with back-off");
        assertThat(tooMany.replaceAll("\\s+", " "))
            .as("the limit is one bucket per gateway pod shared by every caller, not a per-client quota")
            .contains("shared 50 requests per second per gateway pod for all callers");
    }

    @Test
    void the429DoesNotPromiseARetryAfterHeaderTheGatewayNeverSends() throws IOException {
        // The mesh local rate limit (Envoy local_ratelimit) adds only x-fbx-rate-limited.
        String yaml = Files.readString(Path.of("api/openapi/atm-directory-service.yaml"));
        String tooMany = yaml.substring(yaml.indexOf("'429':"), yaml.indexOf("'503':"));

        assertThat(tooMany).doesNotContain("Retry-After", "Retry after the given number of seconds");
    }
}
