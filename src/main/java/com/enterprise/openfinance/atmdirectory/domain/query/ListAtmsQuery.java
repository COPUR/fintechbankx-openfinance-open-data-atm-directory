package com.enterprise.openfinance.atmdirectory.domain.query;

import com.enterprise.openfinance.atmdirectory.domain.model.Coordinates;

/**
 * ATM directory query. Either no location (whole directory) or both coordinates
 * with an optional radius in (0, 50] km; a radius without coordinates is ignored.
 */
public record ListAtmsQuery(Double latitude, Double longitude, Double radiusKm) {

    public static final double DEFAULT_RADIUS_KM = 10.0d;
    public static final double MAX_RADIUS_KM = 50.0d;

    public ListAtmsQuery {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude must be provided together");
        }
        if (latitude == null) {
            radiusKm = null;
        } else {
            Coordinates.requireValid(latitude, longitude);
            if (radiusKm != null && !(radiusKm > 0 && radiusKm <= MAX_RADIUS_KM)) {
                throw new IllegalArgumentException("radiusKm must be greater than 0 and at most 50");
            }
        }
    }

    public boolean hasGeoFilter() {
        return latitude != null;
    }

    public double effectiveRadiusKm() {
        return radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
    }
}
