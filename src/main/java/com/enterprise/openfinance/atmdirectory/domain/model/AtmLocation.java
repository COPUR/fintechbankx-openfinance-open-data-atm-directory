package com.enterprise.openfinance.atmdirectory.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One ATM of the bank's network as published in the open-data directory.
 * Public reference data: no customer data is attached to it.
 */
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
    String currency,
    Instant updatedAt
) {
    private static final Pattern ISO_4217 = Pattern.compile("[A-Z]{3}");

    public AtmLocation {
        requireNonBlank(atmId, "atmId");
        requireNonBlank(name, "name");
        requireNonBlank(status, "status");
        requireNonBlank(address, "address");
        requireNonBlank(city, "city");
        requireNonBlank(country, "country");
        requireNonBlank(accessibility, "accessibility");
        Coordinates.requireValid(latitude, longitude);
        services = List.copyOf(Objects.requireNonNull(services, "services must not be null"));
        if (services.isEmpty() || services.stream().anyMatch(service -> service == null || service.isBlank())) {
            throw new IllegalArgumentException("services must contain at least one non-blank service");
        }
        if (currency == null || !ISO_4217.matcher(currency).matches()) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code, got '" + currency + "'");
        }
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
