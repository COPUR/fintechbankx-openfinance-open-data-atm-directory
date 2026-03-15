package com.enterprise.openfinance.atmdirectory.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AtmDirectoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectWhenInteractionHeaderMissing() throws Exception {
        mockMvc.perform(get("/open-finance/v1/atms"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HEADER_MISSING"));
    }

    @Test
    void shouldReturnAtmsWithHeaders() throws Exception {
        mockMvc.perform(get("/open-finance/v1/atms")
                        .header("X-FAPI-Interaction-ID", "atm-interaction-1")
                        .param("lat", "25.2048")
                        .param("long", "55.2708")
                        .param("radius", "15"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(header().string("X-FAPI-Interaction-ID", "atm-interaction-1"))
                .andExpect(jsonPath("$.Data.ATM.length()").value(1));
    }

    @Test
    void shouldReturnNotModifiedWhenIfNoneMatchMatches() throws Exception {
        String etag = mockMvc.perform(get("/open-finance/v1/atms")
                        .header("X-FAPI-Interaction-ID", "atm-interaction-2"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mockMvc.perform(get("/open-finance/v1/atms")
                        .header("X-FAPI-Interaction-ID", "atm-interaction-2")
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }
}
