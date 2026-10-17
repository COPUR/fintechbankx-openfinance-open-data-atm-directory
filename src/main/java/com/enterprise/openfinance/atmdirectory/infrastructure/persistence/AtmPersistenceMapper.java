package com.enterprise.openfinance.atmdirectory.infrastructure.persistence;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import java.util.List;

/** Maps between the domain model and the JPA row; the version stays a storage concern. */
final class AtmPersistenceMapper {

    private AtmPersistenceMapper() {
    }

    static AtmLocation toDomain(AtmJpaEntity row) {
        return new AtmLocation(
            row.getAtmId(),
            row.getName(),
            row.getStatus(),
            row.getLatitude(),
            row.getLongitude(),
            row.getAddressLine(),
            row.getCity(),
            row.getCountryCode(),
            row.getAccessibility(),
            row.getServices() == null ? List.of() : List.of(row.getServices()),
            row.getCurrency(),
            row.getUpdatedAt());
    }

    static AtmJpaEntity toEntity(AtmLocation atm, long version) {
        return new AtmJpaEntity(
            atm.atmId(),
            atm.name(),
            atm.status(),
            atm.latitude(),
            atm.longitude(),
            atm.address(),
            atm.city(),
            atm.country(),
            atm.accessibility(),
            atm.services().toArray(String[]::new),
            atm.currency(),
            atm.updatedAt(),
            version);
    }
}
