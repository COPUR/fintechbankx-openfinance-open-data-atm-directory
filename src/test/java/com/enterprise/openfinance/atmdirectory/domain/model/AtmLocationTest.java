package com.enterprise.openfinance.atmdirectory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AtmLocationTest {

    private static final Instant UPDATED = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void shouldCreateValidAtmLocation() {
        AtmLocation location = atm(25.2048, 55.2708, List.of("CashWithdrawal", "CashDeposit"), "AED");

        assertThat(location.atmId()).isEqualTo("ATM-1");
        assertThat(location.latitude()).isEqualTo(25.2048);
        assertThat(location.currency()).isEqualTo("AED");
        assertThat(location.services()).containsExactly("CashWithdrawal", "CashDeposit");
    }

    @Test
    void servicesAreCopiedSoCallersCannotChangeTheAtm() {
        List<String> services = new ArrayList<>(List.of("CashWithdrawal"));
        AtmLocation location = atm(25.2, 55.2, services, "AED");

        services.add("CashDeposit");

        assertThat(location.services()).containsExactly("CashWithdrawal");
    }

    @Test
    void shouldRejectEmptyServices() {
        assertThatThrownBy(() -> atm(25.2, 55.2, List.of(), "AED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("services");
    }

    @Test
    void shouldRejectBlankServiceName() {
        assertThatThrownBy(() -> atm(25.2, 55.2, List.of("CashWithdrawal", " "), "AED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("services");
    }

    @Test
    void shouldRejectLatitudeOutsideTheGlobe() {
        assertThatThrownBy(() -> atm(90.0001, 55.2, List.of("CashWithdrawal"), "AED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("latitude");
    }

    @Test
    void shouldRejectLongitudeOutsideTheGlobe() {
        assertThatThrownBy(() -> atm(25.2, -180.5, List.of("CashWithdrawal"), "AED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("longitude");
    }

    @Test
    void shouldRejectCurrencyThatIsNotAnIsoCode() {
        assertThatThrownBy(() -> atm(25.2, 55.2, List.of("CashWithdrawal"), "aed"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currency");
        assertThatThrownBy(() -> atm(25.2, 55.2, List.of("CashWithdrawal"), "DIRHAM"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currency");
    }

    @Test
    void shouldRejectBlankIdentityAndMissingTimestamp() {
        assertThatThrownBy(() -> new AtmLocation(" ", "Downtown", "InService", 25.2, 55.2,
            "Road 1", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), "AED", UPDATED))
            .hasMessageContaining("atmId");
        assertThatThrownBy(() -> new AtmLocation("ATM-1", "Downtown", "InService", 25.2, 55.2,
            "Road 1", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), "AED", null))
            .isInstanceOf(NullPointerException.class);
    }

    private static AtmLocation atm(double lat, double lon, List<String> services, String currency) {
        return new AtmLocation("ATM-1", "Downtown", "InService", lat, lon,
            "Road 1", "Dubai", "AE", "Wheelchair", services, currency, UPDATED);
    }
}
