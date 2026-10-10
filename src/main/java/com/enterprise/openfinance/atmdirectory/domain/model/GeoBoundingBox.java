package com.enterprise.openfinance.atmdirectory.domain.model;

/**
 * Latitude/longitude rectangle that encloses a circle on the Earth's surface.
 * Used as an index-friendly pre-filter; callers still apply the exact
 * great-circle distance to drop the rectangle's corners.
 */
public record GeoBoundingBox(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {

    private static final double KM_PER_DEGREE_LATITUDE = Math.PI * Coordinates.EARTH_RADIUS_KM / 180.0;

    public GeoBoundingBox {
        Coordinates.requireLatitude(minLatitude);
        Coordinates.requireLatitude(maxLatitude);
        Coordinates.requireLongitude(minLongitude);
        Coordinates.requireLongitude(maxLongitude);
        if (minLatitude > maxLatitude) {
            throw new IllegalArgumentException("minLatitude must not exceed maxLatitude");
        }
        if (minLongitude > maxLongitude) {
            throw new IllegalArgumentException("minLongitude must not exceed maxLongitude");
        }
    }

    public static GeoBoundingBox around(double latitude, double longitude, double radiusKm) {
        Coordinates.requireValid(latitude, longitude);
        if (!(radiusKm >= 0.0)) {
            throw new IllegalArgumentException("radiusKm must be >= 0, got " + radiusKm);
        }
        double latDelta = radiusKm / KM_PER_DEGREE_LATITUDE;
        double minLat = Math.max(-90.0, latitude - latDelta);
        double maxLat = Math.min(90.0, latitude + latDelta);
        if (minLat == -90.0 || maxLat == 90.0) {
            // A circle that reaches a pole spans every meridian.
            return new GeoBoundingBox(minLat, maxLat, -180.0, 180.0);
        }
        double lonDelta = latDelta / Math.cos(Math.toRadians(latitude));
        double minLon = longitude - lonDelta;
        double maxLon = longitude + lonDelta;
        if (minLon < -180.0 || maxLon > 180.0) {
            // Crossing the antimeridian: one rectangle cannot express the wrap, so widen.
            return new GeoBoundingBox(minLat, maxLat, -180.0, 180.0);
        }
        return new GeoBoundingBox(minLat, maxLat, minLon, maxLon);
    }

    public boolean contains(double latitude, double longitude) {
        return latitude >= minLatitude && latitude <= maxLatitude
            && longitude >= minLongitude && longitude <= maxLongitude;
    }
}
