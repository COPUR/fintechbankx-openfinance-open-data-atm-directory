package com.enterprise.openfinance.atmdirectory.infrastructure.functional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.openfinance.atmdirectory.infrastructure.config.AtmDirectoryConfiguration;
import com.enterprise.openfinance.atmdirectory.infrastructure.web.AtmDirectoryController;
import com.enterprise.openfinance.atmdirectory.support.InMemoryAtmDirectoryAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer and use case together over the sample directory, no database. */
@WebMvcTest(controllers = AtmDirectoryController.class)
@Import({AtmDirectoryConfiguration.class, InMemoryAtmDirectoryAdapter.class})
class AtmDirectoryUatTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldSupportEtagNotModifiedForRepeatedAtmQueries() throws Exception {
        String etag = mockMvc.perform(get("/open-finance/v1/atms").header("X-FAPI-Interaction-ID", "uat-001"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-OF-Cache", "MISS"))
            .andExpect(jsonPath("$.Meta.TotalRecords").value(3))
            .andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get("/open-finance/v1/atms")
                .header("X-FAPI-Interaction-ID", "uat-001")
                .header("If-None-Match", etag))
            .andExpect(status().isNotModified())
            .andExpect(header().string("X-OF-Cache", "HIT"));
    }

    @Test
    void radiusSearchAroundDowntownDubaiFindsDowntownAndMarina() throws Exception {
        mockMvc.perform(get("/open-finance/v1/atms?lat=25.2048&long=55.2708&radius=25")
                .header("X-FAPI-Interaction-ID", "uat-002"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.Data.ATM[*].AtmId").value(org.hamcrest.Matchers.contains("ATM-001", "ATM-002")))
            .andExpect(jsonPath("$.Links.Self").value("/open-finance/v1/atms?lat=25.2048&long=55.2708&radius=25.0"));
    }
}
