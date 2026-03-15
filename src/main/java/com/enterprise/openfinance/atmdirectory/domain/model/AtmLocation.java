package com.enterprise.openfinance.atmdirectory.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AtmLocation(
    String atmId,
    String name,
    String status,
    double latitude,
    double longitude,
    String address,
    String city,
    String country,
    String accessibility,
    List<String> services,
    Instant updatedAt
) {
    public AtmLocation {
        requireNonBlank(atmId, "atmId");
        requireNonBlank(name, "name");
        requireNonBlank(status, "status");
        requireNonBlank(address, "address");
        requireNonBlank(city, "city");
        requireNonBlank(country, "country");
        requireNonBlank(accessibility, "accessibility");
        services = List.copyOf(services);
        if (services.isEmpty()) {
            throw new IllegalArgumentException("services must not be empty");
        }
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
