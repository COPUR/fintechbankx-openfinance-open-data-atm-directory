package com.enterprise.openfinance.atmdirectory.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record Atm(
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
        OffsetDateTime updatedAt
) {
}
