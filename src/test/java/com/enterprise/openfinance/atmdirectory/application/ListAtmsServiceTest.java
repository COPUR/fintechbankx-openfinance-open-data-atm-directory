package com.enterprise.openfinance.atmdirectory.application;

import com.enterprise.openfinance.atmdirectory.application.port.out.AtmDirectoryReadPort;
import com.enterprise.openfinance.atmdirectory.domain.Atm;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListAtmsServiceTest {

    @Test
    void shouldFilterByDistanceWhenLocationProvided() {
        AtmDirectoryReadPort port = Mockito.mock(AtmDirectoryReadPort.class);
        Mockito.when(port.findAll()).thenReturn(List.of(
                new Atm("ATM-1", "Main Branch ATM", "InService", 25.2048, 55.2708, "Downtown", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), OffsetDateTime.parse("2026-03-01T00:00:00Z")),
                new Atm("ATM-2", "Far Branch ATM", "InService", 24.4539, 54.3773, "Corniche", "Abu Dhabi", "AE", "Wheelchair", List.of("CashWithdrawal"), OffsetDateTime.parse("2026-03-01T00:00:00Z"))
        ));

        ListAtmsService service = new ListAtmsService(port);
        List<Atm> atms = service.list(25.2048, 55.2708, 10.0);

        assertThat(atms).hasSize(1);
        assertThat(atms.getFirst().atmId()).isEqualTo("ATM-1");
    }

    @Test
    void shouldReturnAllWhenLocationNotProvided() {
        AtmDirectoryReadPort port = Mockito.mock(AtmDirectoryReadPort.class);
        Mockito.when(port.findAll()).thenReturn(List.of(
                new Atm("ATM-1", "Main Branch ATM", "InService", 25.2048, 55.2708, "Downtown", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), OffsetDateTime.parse("2026-03-01T00:00:00Z")),
                new Atm("ATM-2", "Far Branch ATM", "InService", 24.4539, 54.3773, "Corniche", "Abu Dhabi", "AE", "Wheelchair", List.of("CashWithdrawal"), OffsetDateTime.parse("2026-03-01T00:00:00Z"))
        ));

        ListAtmsService service = new ListAtmsService(port);
        List<Atm> atms = service.list(null, null, null);

        assertThat(atms).hasSize(2);
    }
}
