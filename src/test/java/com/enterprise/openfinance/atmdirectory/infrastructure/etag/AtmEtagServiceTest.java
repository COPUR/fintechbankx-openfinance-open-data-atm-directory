package com.enterprise.openfinance.atmdirectory.infrastructure.etag;

import com.enterprise.openfinance.atmdirectory.domain.Atm;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AtmEtagServiceTest {

    private final AtmEtagService service = new AtmEtagService();

    @Test
    void shouldGenerateStableHashForSamePayload() {
        List<Atm> atms = List.of(
                new Atm("ATM-1", "Main Branch ATM", "InService", 25.2048, 55.2708, "Downtown", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), OffsetDateTime.parse("2026-03-01T00:00:00Z"))
        );
        String left = service.compute(atms, 25.2, 55.2, 5.0);
        String right = service.compute(atms, 25.2, 55.2, 5.0);
        assertThat(left).isEqualTo(right);
    }

    @Test
    void shouldChangeHashWhenStatusChanges() {
        List<Atm> left = List.of(
                new Atm("ATM-1", "Main Branch ATM", "InService", 25.2048, 55.2708, "Downtown", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), OffsetDateTime.parse("2026-03-01T00:00:00Z"))
        );
        List<Atm> right = List.of(
                new Atm("ATM-1", "Main Branch ATM", "OutOfService", 25.2048, 55.2708, "Downtown", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), OffsetDateTime.parse("2026-03-01T00:00:00Z"))
        );
        assertThat(service.compute(left, null, null, null)).isNotEqualTo(service.compute(right, null, null, null));
    }
}
