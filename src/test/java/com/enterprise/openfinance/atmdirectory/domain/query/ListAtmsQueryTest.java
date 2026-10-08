package com.enterprise.openfinance.atmdirectory.domain.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ListAtmsQueryTest {

    @Test
    void geoFilterNeedsBothCoordinates() {
        assertThat(new ListAtmsQuery(25.2, 55.2, 5.0).hasGeoFilter()).isTrue();
        assertThat(new ListAtmsQuery(25.2, null, 5.0).hasGeoFilter()).isFalse();
        assertThat(new ListAtmsQuery(null, null, null).hasGeoFilter()).isFalse();
    }

    @Test
    void radiusDefaultsToTenKilometres() {
        assertThat(new ListAtmsQuery(25.2, 55.2, null).effectiveRadiusKm()).isEqualTo(10.0);
        assertThat(new ListAtmsQuery(25.2, 55.2, 3.5).effectiveRadiusKm()).isEqualTo(3.5);
    }

    @Test
    void rejectsNegativeRadiusAndCoordinatesOutsideTheGlobe() {
        assertThatThrownBy(() -> new ListAtmsQuery(25.2, 55.2, -1.0)).hasMessageContaining("radiusKm");
        assertThatThrownBy(() -> new ListAtmsQuery(91.0, 55.2, 1.0)).hasMessageContaining("latitude");
        assertThatThrownBy(() -> new ListAtmsQuery(25.2, 181.0, 1.0)).hasMessageContaining("longitude");
    }
}
