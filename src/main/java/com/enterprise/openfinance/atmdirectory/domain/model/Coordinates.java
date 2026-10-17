package com.enterprise.openfinance.atmdirectory.domain.model;

/** WGS84 coordinate rules shared by the directory model and its queries. */
public final class Coordinates {

    public static final double EARTH_RADIUS_KM = 6371.0;

    private Coordinates() {
    }

    public static void requireValid(double latitude, double longitude) {
        requireLatitude(latitude);
        requireLongitude(longitude);
    }

    public static void requireLatitude(double latitude) {
        if (!(latitude >= -90.0 && latitude <= 90.0)) {
            throw new IllegalArgumentException("latitude must be between -90 and 90, got " + latitude);
        }
    }

    public static void requireLongitude(double longitude) {
        if (!(longitude >= -180.0 && longitude <= 180.0)) {
            throw new IllegalArgumentException("longitude must be between -180 and 180, got " + longitude);
        }
    }
}
