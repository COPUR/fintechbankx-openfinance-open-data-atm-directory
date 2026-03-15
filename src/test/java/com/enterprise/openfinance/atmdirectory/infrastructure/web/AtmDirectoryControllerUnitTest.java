package com.enterprise.openfinance.atmdirectory.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@WebMvcTest(controllers = AtmDirectoryController.class)
class AtmDirectoryControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AtmDirectoryUseCase atmDirectoryUseCase;

    @Test
    void shouldReturnAtmList() throws Exception {
        when(atmDirectoryUseCase.listAtms(any())).thenReturn(new AtmListResult(List.of(
            new AtmLocation("ATM-001", "Downtown", "InService", 25.2, 55.2,
                "Road 1", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), Instant.parse("2026-03-01T00:00:00Z"))
        )));

        mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-001"))
            .andExpect(status().isOk())
            .andExpect(header().exists("ETag"))
            .andExpect(jsonPath("$.Data.ATM[0].AtmId").value("ATM-001"));
    }

    @Test
    void shouldReturnNotModifiedWhenIfNoneMatchMatches() throws Exception {
        when(atmDirectoryUseCase.listAtms(any())).thenReturn(new AtmListResult(List.of(
            new AtmLocation("ATM-001", "Downtown", "InService", 25.2, 55.2,
                "Road 1", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), Instant.parse("2026-03-01T00:00:00Z"))
        )));

        String etag = mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "it-001"))
            .andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get("/open-finance/v1/atms")
                .header("X-FAPI-Interaction-ID", "it-001")
                .header("If-None-Match", etag))
            .andExpect(status().isNotModified())
            .andExpect(header().string("X-OF-Cache", "HIT"));
    }

    @Test
    void shouldFailWhenMissingInteractionHeader() throws Exception {
        mockMvc.perform(get("/open-finance/v1/atms"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
