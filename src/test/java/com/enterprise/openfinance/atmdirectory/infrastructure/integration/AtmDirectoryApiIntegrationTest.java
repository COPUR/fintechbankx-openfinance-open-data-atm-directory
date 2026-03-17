package com.enterprise.openfinance.atmdirectory.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.openfinance.atmdirectory.infrastructure.web.dto.AtmListResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AtmDirectoryApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldFilterAtmsByLocationRadius() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-FAPI-Interaction-ID", "it-int-001");

        ResponseEntity<AtmListResponse> response = restTemplate.exchange(
            "http://localhost:" + port + "/open-finance/v1/atms?lat=25.2048&long=55.2708&radius=25",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            AtmListResponse.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().meta().totalRecords()).isGreaterThanOrEqualTo(1);
    }
}
