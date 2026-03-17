package com.enterprise.openfinance.atmdirectory.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AtmLocationTest {

    @Test
    void shouldCreateValidAtmLocation() {
        AtmLocation location = new AtmLocation(
            "ATM-1", "Downtown", "InService", 25.2, 55.2,
            "Road 1", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), Instant.parse("2026-03-01T00:00:00Z")
        );

        assertEquals("ATM-1", location.atmId());
    }

    @Test
    void shouldRejectEmptyServices() {
        assertThrows(IllegalArgumentException.class, () -> new AtmLocation(
            "ATM-1", "Downtown", "InService", 25.2, 55.2,
            "Road 1", "Dubai", "AE", "Wheelchair", List.of(), Instant.parse("2026-03-01T00:00:00Z")
        ));
    }
}
