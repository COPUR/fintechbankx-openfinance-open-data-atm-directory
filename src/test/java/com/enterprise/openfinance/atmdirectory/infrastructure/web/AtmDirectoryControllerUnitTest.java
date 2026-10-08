package com.enterprise.openfinance.atmdirectory.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.openfinance.atmdirectory.domain.exception.AtmDirectoryUnavailableException;
import com.enterprise.openfinance.atmdirectory.domain.model.AtmListResult;
import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.port.in.AtmDirectoryUseCase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AtmDirectoryController.class, properties = "atm-directory.cache.max-age=PT120S")
class AtmDirectoryControllerUnitTest {

    private static final AtmLocation DOWNTOWN = new AtmLocation("ATM-001", "Downtown", "InService", 25.2, 55.2,
        "Road 1", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), "AED", Instant.parse("2026-03-01T00:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AtmDirectoryUseCase atmDirectoryUseCase;

    @Test
    void shouldReturnAtmListWithCacheHeaders() throws Exception {
        when(atmDirectoryUseCase.listAtms(any())).thenReturn(new AtmListResult(List.of(DOWNTOWN)));

        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-001"))
            .andExpect(status().isOk())
            .andExpect(header().exists("ETag"))
            .andExpect(header().string("Cache-Control", "max-age=120, public"))
            .andExpect(header().string("X-FAPI-Interaction-ID", "it-001"))
            .andExpect(jsonPath("$.Data.ATM[0].AtmId").value("ATM-001"))
            .andExpect(jsonPath("$.Data.ATM[0].Currency").value("AED"))
            .andExpect(jsonPath("$.Meta.TotalRecords").value(1));
    }

    @Test
    void shouldReturnNotModifiedWhenIfNoneMatchMatches() throws Exception {
        when(atmDirectoryUseCase.listAtms(any())).thenReturn(new AtmListResult(List.of(DOWNTOWN)));

        String etag = mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-001"))
            .andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get("/open-finance/v1/atms")
                .header("X-FAPI-Interaction-ID", "it-001")
                .header("If-None-Match", etag))
            .andExpect(status().isNotModified())
            .andExpect(header().string("ETag", etag))
            .andExpect(header().string("Cache-Control", "max-age=120, public"))
            .andExpect(header().string("X-OF-Cache", "HIT"));
    }

    @Test
    void etagChangesWhenAnAtmChanges() {
        AtmLocation moved = new AtmLocation("ATM-001", "Downtown", "OutOfService", 25.2, 55.2,
            "Road 1", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), "AED", Instant.parse("2026-03-04T00:00:00Z"));

        assertThat(AtmDirectoryController.toEtag(List.of(DOWNTOWN)))
            .isNotEqualTo(AtmDirectoryController.toEtag(List.of(moved)))
            .startsWith("\"").endsWith("\"");
    }

    @Test
    void shouldFailWhenMissingInteractionHeader() throws Exception {
        mockMvc.perform(get("/open-finance/v1/atms"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void latitudeOutsideTheGlobeIsABadRequest() throws Exception {
        mockMvc.perform(get("/open-finance/v1/atms?lat=95&long=55&radius=5").header("X-FAPI-Interaction-ID", "it-002"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.interactionId").value("it-002"));
    }

    @Test
    void latitudeWithoutLongitudeIsABadRequest() throws Exception {
        mockMvc.perform(get("/open-finance/v1/atms?lat=25.2").header("X-FAPI-Interaction-ID", "it-007"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("latitude and longitude must be provided together"));
    }

    @Test
    void radiusAboveFiftyKilometresIsABadRequest() throws Exception {
        mockMvc.perform(get("/open-finance/v1/atms?lat=25.2&long=55.2&radius=51").header("X-FAPI-Interaction-ID", "it-008"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void blankInteractionIdIsABadRequest() throws Exception {
        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "  "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.interactionId").value("UNKNOWN"));
    }

    @Test
    void authorizationHeaderIsOptionalButMustBeBearerOrDpop() throws Exception {
        when(atmDirectoryUseCase.listAtms(any())).thenReturn(new AtmListResult(List.of(DOWNTOWN)));

        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-009")
                .header("Authorization", "Bearer abc"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-009")
                .header("Authorization", "DPoP abc"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-009")
                .header("Authorization", "Basic dXNlcjpwYXNz"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Authorization header must use Bearer or DPoP token type"));
    }

    @Test
    void nonNumericCoordinateIsABadRequest() throws Exception {
        mockMvc.perform(get("/open-finance/v1/atms?lat=north").header("X-FAPI-Interaction-ID", "it-003"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Query parameter 'lat' has an invalid value"));
    }

    @Test
    void unavailableStoreIsA503WithRetryAfter() throws Exception {
        when(atmDirectoryUseCase.listAtms(any()))
            .thenThrow(new AtmDirectoryUnavailableException("down", new IllegalStateException("db")));

        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-004"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "5"))
            .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void clientDisconnectIsNotReportedAsAServerError() throws Exception {
        when(atmDirectoryUseCase.listAtms(any())).thenAnswer(invocation -> {
            throw new org.springframework.web.context.request.async.AsyncRequestNotUsableException("connection reset");
        });

        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-006"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
    }

    @Test
    void unexpectedErrorIsA500WithoutDetails() throws Exception {
        when(atmDirectoryUseCase.listAtms(any())).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-005"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
