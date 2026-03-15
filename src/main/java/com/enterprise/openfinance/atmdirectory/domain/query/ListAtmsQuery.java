package com.enterprise.openfinance.atmdirectory.domain.query;

public record ListAtmsQuery(Double latitude, Double longitude, Double radiusKm) {
    public ListAtmsQuery {
        if (radiusKm != null && radiusKm < 0) {
            throw new IllegalArgumentException("radiusKm must be >= 0");
        }
    }
}
