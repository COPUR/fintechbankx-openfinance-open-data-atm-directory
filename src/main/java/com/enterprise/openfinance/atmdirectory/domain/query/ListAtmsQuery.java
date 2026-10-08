package com.enterprise.openfinance.atmdirectory.domain.query;

import com.enterprise.openfinance.atmdirectory.domain.model.Coordinates;

public record ListAtmsQuery(Double latitude, Double longitude, Double radiusKm) {

    public static final double DEFAULT_RADIUS_KM = 10.0d;

    public ListAtmsQuery {
        if (radiusKm != null && !(radiusKm >= 0)) {
            throw new IllegalArgumentException("radiusKm must be >= 0");
        }
        if (latitude != null) {
            Coordinates.requireLatitude(latitude);
        }
        if (longitude != null) {
            Coordinates.requireLongitude(longitude);
        }
    }

    public boolean hasGeoFilter() {
        return latitude != null && longitude != null;
    }

    public double effectiveRadiusKm() {
        return radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
    }
}
