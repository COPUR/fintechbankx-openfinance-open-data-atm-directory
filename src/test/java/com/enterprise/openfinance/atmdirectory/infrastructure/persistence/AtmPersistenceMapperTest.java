package com.enterprise.openfinance.atmdirectory.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AtmPersistenceMapperTest {

    private static final AtmLocation MARINA = new AtmLocation("ATM-002", "Marina ATM", "InService", 25.08, 55.14,
        "Dubai Marina Walk", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal", "CashDeposit"), "AED",
        Instant.parse("2026-03-02T00:00:00Z"));

    @Test
    void roundTripKeepsEveryDomainField() {
        AtmJpaEntity row = AtmPersistenceMapper.toEntity(MARINA, 4);

        assertThat(row.getVersion()).isEqualTo(4);
        assertThat(row.getAddressLine()).isEqualTo("Dubai Marina Walk");
        assertThat(row.getCountryCode()).isEqualTo("AE");
        assertThat(row.getServices()).containsExactly("CashWithdrawal", "CashDeposit");
        assertThat(AtmPersistenceMapper.toDomain(row)).isEqualTo(MARINA);
    }

    @Test
    void entityDoesNotLeakItsServicesArray() {
        AtmJpaEntity row = AtmPersistenceMapper.toEntity(MARINA, 0);

        row.getServices()[0] = "Changed";

        assertThat(row.getServices()[0]).isEqualTo("CashWithdrawal");
    }

    @Test
    void rowThatBreaksDomainRulesIsRejectedOnRead() {
        AtmJpaEntity row = new AtmJpaEntity("ATM-9", "Broken", "InService", 25.0, 55.0, "Road", "Dubai", "AE",
            "Standard", null, "AED", Instant.parse("2026-03-02T00:00:00Z"), 0);

        assertThatThrownBy(() -> AtmPersistenceMapper.toDomain(row))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("services");
        assertThat(new AtmJpaEntity().getServices()).isNull();
    }
}
