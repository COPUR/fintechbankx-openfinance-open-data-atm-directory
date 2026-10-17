package com.enterprise.openfinance.atmdirectory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row of sc_of_atm_directory.atm. Read-only for the service: rows are written
 * by Flyway (seed) and db/import/import-atms.sh, so the entity is immutable.
 */
@Entity
@Immutable
@Table(name = "atm")
public class AtmJpaEntity {

    @Id
    @Column(name = "atm_id", length = 64, nullable = false)
    private String atmId;

    @Column(name = "name", length = 140, nullable = false)
    private String name;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "address_line", length = 255, nullable = false)
    private String addressLine;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode;

    @Column(name = "accessibility", length = 64, nullable = false)
    private String accessibility;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "services", nullable = false, columnDefinition = "text[]")
    private String[] services;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "version", nullable = false)
    private long version;

    protected AtmJpaEntity() {
        // for JPA
    }

    AtmJpaEntity(String atmId, String name, String status, double latitude, double longitude,
                 String addressLine, String city, String countryCode, String accessibility,
                 String[] services, String currency, Instant updatedAt, long version) {
        this.atmId = atmId;
        this.name = name;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.addressLine = addressLine;
        this.city = city;
        this.countryCode = countryCode;
        this.accessibility = accessibility;
        this.services = services == null ? null : services.clone();
        this.currency = currency;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    String getAtmId() { return atmId; }
    String getName() { return name; }
    String getStatus() { return status; }
    double getLatitude() { return latitude; }
    double getLongitude() { return longitude; }
    String getAddressLine() { return addressLine; }
    String getCity() { return city; }
    String getCountryCode() { return countryCode; }
    String getAccessibility() { return accessibility; }
    String[] getServices() { return services == null ? null : services.clone(); }
    String getCurrency() { return currency; }
    Instant getUpdatedAt() { return updatedAt; }
    long getVersion() { return version; }
}
