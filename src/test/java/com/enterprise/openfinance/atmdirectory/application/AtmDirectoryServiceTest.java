package com.enterprise.openfinance.atmdirectory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.port.out.AtmDirectoryPort;
import com.enterprise.openfinance.atmdirectory.domain.query.ListAtmsQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AtmDirectoryServiceTest {

    @Mock
    private AtmDirectoryPort atmDirectoryPort;

    private AtmDirectoryService service;

    @BeforeEach
    void setUp() {
        service = new AtmDirectoryService(atmDirectoryPort);
    }

    @Test
    void shouldReturnOnlyNearbyAtmsWhenLatLongProvided() {
        when(atmDirectoryPort.findAll()).thenReturn(List.of(
            new AtmLocation("A", "Near", "InService", 25.2048, 55.2708, "R1", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), Instant.parse("2026-03-01T00:00:00Z")),
            new AtmLocation("B", "Far", "InService", 24.4950, 54.3820, "R2", "Abu Dhabi", "AE", "Standard", List.of("CashWithdrawal"), Instant.parse("2026-03-01T00:00:00Z"))
        ));

        var result = service.listAtms(new ListAtmsQuery(25.2048, 55.2708, 20.0));

        assertEquals(1, result.atms().size());
        assertEquals("A", result.atms().getFirst().atmId());
    }

    @Test
    void shouldReturnAllAtmsWhenNoGeoFilter() {
        when(atmDirectoryPort.findAll()).thenReturn(List.of(
            new AtmLocation("A", "Near", "InService", 25.2048, 55.2708, "R1", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), Instant.parse("2026-03-01T00:00:00Z")),
            new AtmLocation("B", "Far", "InService", 24.4950, 54.3820, "R2", "Abu Dhabi", "AE", "Standard", List.of("CashWithdrawal"), Instant.parse("2026-03-01T00:00:00Z"))
        ));

        var result = service.listAtms(new ListAtmsQuery(null, null, null));

        assertEquals(2, result.atms().size());
    }
}
