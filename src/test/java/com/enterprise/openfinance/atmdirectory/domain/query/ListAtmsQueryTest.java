package com.enterprise.openfinance.atmdirectory.domain.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ListAtmsQueryTest {

    @Test
    void geoFilterNeedsBothCoordinates() {
        assertThat(new ListAtmsQuery(25.2, 55.2, 5.0).hasGeoFilter()).isTrue();
        assertThat(new ListAtmsQuery(null, null, null).hasGeoFilter()).isFalse();
    }

    @Test
    void latitudeAndLongitudeMustBeGivenTogether() {
        // Monolith parity (open-finance-context GetAtmsQuery): one coordinate alone is a bad request.
        assertThatThrownBy(() -> new ListAtmsQuery(25.2, null, 5.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("latitude and longitude must be provided together");
        assertThatThrownBy(() -> new ListAtmsQuery(null, 55.2, null))
            .hasMessage("latitude and longitude must be provided together");
    }

    @Test
    void radiusDefaultsToFiveKilometresLikeTheMonolith() {
        assertThat(new ListAtmsQuery(25.2, 55.2, null).effectiveRadiusKm()).isEqualTo(5.0);
        assertThat(new ListAtmsQuery(25.2, 55.2, 3.5).effectiveRadiusKm()).isEqualTo(3.5);
    }

    @Test
    void radiusMustBePositiveAndAtMostFiftyKilometres() {
        assertThat(new ListAtmsQuery(25.2, 55.2, 50.0).effectiveRadiusKm()).isEqualTo(50.0);
        assertThatThrownBy(() -> new ListAtmsQuery(25.2, 55.2, 50.01))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("radiusKm must be greater than 0 and at most 50");
        assertThatThrownBy(() -> new ListAtmsQuery(25.2, 55.2, 0.0))
            .hasMessage("radiusKm must be greater than 0 and at most 50");
        assertThatThrownBy(() -> new ListAtmsQuery(25.2, 55.2, -1.0))
            .hasMessage("radiusKm must be greater than 0 and at most 50");
        assertThatThrownBy(() -> new ListAtmsQuery(25.2, 55.2, Double.NaN))
            .hasMessage("radiusKm must be greater than 0 and at most 50");
    }

    @Test
    void radiusWithoutCoordinatesIsIgnored() {
        ListAtmsQuery query = new ListAtmsQuery(null, null, 500.0);

        assertThat(query.hasGeoFilter()).isFalse();
        assertThat(query.radiusKm()).isNull();
    }

    @Test
    void rejectsCoordinatesOutsideTheGlobe() {
        assertThatThrownBy(() -> new ListAtmsQuery(91.0, 55.2, 1.0)).hasMessageContaining("latitude");
        assertThatThrownBy(() -> new ListAtmsQuery(25.2, 181.0, 1.0)).hasMessageContaining("longitude");
    }
}
