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
    }
}
