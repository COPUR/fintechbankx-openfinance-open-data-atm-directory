package com.enterprise.openfinance.atmdirectory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoBoundingBoxTest {

    @Test
    void boxAroundDowntownDubaiCoversTheRadiusInEveryDirection() {
        // 25 km is 0.22483 degrees of latitude; at 25.2048 N one degree of longitude
        // is cos(25.2048) = 0.90479 as wide, so the longitude half-width is 0.24849.
        GeoBoundingBox box = GeoBoundingBox.around(25.2048, 55.2708, 25.0);

        assertThat(box.minLatitude()).isCloseTo(24.97997, within(1e-5));
        assertThat(box.maxLatitude()).isCloseTo(25.42963, within(1e-5));
        assertThat(box.minLongitude()).isCloseTo(55.02231, within(1e-5));
        assertThat(box.maxLongitude()).isCloseTo(55.51929, within(1e-5));
    }

    @Test
    void zeroRadiusIsTheCentrePoint() {
        GeoBoundingBox box = GeoBoundingBox.around(24.4950, 54.3820, 0.0);

        assertThat(box.minLatitude()).isEqualTo(24.4950);
        assertThat(box.maxLatitude()).isEqualTo(24.4950);
        assertThat(box.minLongitude()).isEqualTo(54.3820);
        assertThat(box.maxLongitude()).isEqualTo(54.3820);
    }

    @Test
    void boxIsClampedAtThePoles() {
        GeoBoundingBox box = GeoBoundingBox.around(89.9, 10.0, 50.0);

        assertThat(box.maxLatitude()).isEqualTo(90.0);
        assertThat(box.minLongitude()).isEqualTo(-180.0);
        assertThat(box.maxLongitude()).isEqualTo(180.0);
    }

    @Test
    void boxCrossingTheAntimeridianWidensToAllLongitudes() {
        GeoBoundingBox box = GeoBoundingBox.around(-17.7, 179.9, 30.0);

        assertThat(box.minLongitude()).isEqualTo(-180.0);
        assertThat(box.maxLongitude()).isEqualTo(180.0);
        assertThat(box.minLatitude()).isLessThan(-17.7);
    }

    @Test
    void radiusLargerThanHalfTheEarthCoversTheGlobe() {
        GeoBoundingBox box = GeoBoundingBox.around(0.0, 0.0, 25_000.0);

        assertThat(box).isEqualTo(new GeoBoundingBox(-90.0, 90.0, -180.0, 180.0));
    }

    @Test
    void rejectsNegativeRadiusAndInvertedCorners() {
        assertThatThrownBy(() -> GeoBoundingBox.around(25.0, 55.0, -1.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("radiusKm");
        assertThatThrownBy(() -> new GeoBoundingBox(26.0, 25.0, 55.0, 56.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxLatitude");
        assertThatThrownBy(() -> new GeoBoundingBox(25.0, 26.0, 56.0, 55.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxLongitude");
    }

    @Test
    void containsUsesInclusiveEdges() {
        GeoBoundingBox box = new GeoBoundingBox(25.0, 26.0, 55.0, 56.0);

        assertThat(box.contains(25.0, 56.0)).isTrue();
        assertThat(box.contains(24.9999, 55.5)).isFalse();
        assertThat(box.contains(25.5, 56.0001)).isFalse();
    }
}
