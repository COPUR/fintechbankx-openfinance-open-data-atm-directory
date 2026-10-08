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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice. Production sets server.forward-headers-strategy=none; the forged-host
 * tests below also hold with a ForwardedHeaderFilter, because no part of the response
 * is built from the request URL.
 */
@WebMvcTest(controllers = AtmDirectoryController.class)
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
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andExpect(header().string("X-FAPI-Interaction-ID", "it-001"))
            .andExpect(jsonPath("$.Data.ATM[0].AtmId").value("ATM-001"))
            .andExpect(jsonPath("$.Data.ATM[0].Currency").value("AED"))
            .andExpect(jsonPath("$.Meta.TotalRecords").value(1));
    }

    @Test
    void etagIsTheSnapshotDigestAndRevalidationNeedsNoRecomputation() throws Exception {
        when(atmDirectoryUseCase.listAtms(any())).thenReturn(new AtmListResult(List.of(DOWNTOWN), "d1g3st"));

        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-010"))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"d1g3st\""));
        mockMvc.perform(get("/open-finance/v1/atms")
                .header("X-FAPI-Interaction-ID", "it-011")
                .header("If-None-Match", "\"d1g3st\""))
            .andExpect(status().isNotModified())
            .andExpect(header().string("X-FAPI-Interaction-ID", "it-011"));
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
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andExpect(header().string("X-OF-Cache", "HIT"));
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
    @Test
    void selfLinkIsRelativeAndBuiltFromTheValidatedQueryLikeTheMonolith() throws Exception {
        when(atmDirectoryUseCase.listAtms(any())).thenReturn(new AtmListResult(List.of(DOWNTOWN)));

        mockMvc.perform(get("/open-finance/v1/atms?lat=25.2048&long=55.2708&radius=2&utm=x")
                .header("X-FAPI-Interaction-ID", "it-010"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Links.Self").value("/open-finance/v1/atms?lat=25.2048&long=55.2708&radius=2.0"));

        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-011"))
            .andExpect(jsonPath("$.Links.Self").value("/open-finance/v1/atms"));
    }

    @Test
    void forwardedHostNeverReachesTheBodyOrTheHeaders() throws Exception {
        when(atmDirectoryUseCase.listAtms(any())).thenReturn(new AtmListResult(List.of(DOWNTOWN)));

        MockHttpServletResponse response = mockMvc.perform(get("/open-finance/v1/atms?lat=25.2048&long=55.2708")
                .header("X-FAPI-Interaction-ID", "it-012")
                .header("X-Forwarded-Host", "evil.example")
                .header("X-Forwarded-Proto", "https")
                .header("Forwarded", "host=evil.example;proto=https"))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        assertThat(response.getContentAsString()).doesNotContain("evil.example");
        for (String name : response.getHeaderNames()) {
            assertThat(response.getHeaders(name)).as("header %s", name).noneMatch(value -> value.contains("evil.example"));
        }
    }

    @Test
    void sharedCachesMustRevalidateSoNoCallerGetsAnotherCallersInteractionId() throws Exception {
        when(atmDirectoryUseCase.listAtms(any())).thenReturn(new AtmListResult(List.of(DOWNTOWN)));

        String etag = mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "caller-a"))
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andExpect(header().string("X-FAPI-Interaction-ID", "caller-a"))
            .andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "caller-b").header("If-None-Match", etag))
            .andExpect(status().isNotModified())
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andExpect(header().string("X-FAPI-Interaction-ID", "caller-b"));
    }
    /** Anonymous endpoint: every query value is bounded before it reaches the store. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "lat=NaN&long=55.2", "lat=25.2&long=Infinity", "lat=-Infinity&long=55.2",
        "lat=25.2&long=55.2&radius=NaN", "lat=25.2&long=55.2&radius=Infinity", "lat=25.2&long=55.2&radius=1e308",
        "lat=1e400&long=55.2"})
    void unboundedQueryValuesAreRejectedBeforeTheStoreIsCalled(String query) throws Exception {
        mockMvc.perform(get("/open-finance/v1/atms?" + query).header("X-FAPI-Interaction-ID", "it-013"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        org.mockito.Mockito.verifyNoInteractions(atmDirectoryUseCase);
    }
}
