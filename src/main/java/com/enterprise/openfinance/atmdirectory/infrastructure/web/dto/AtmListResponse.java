package com.enterprise.openfinance.atmdirectory.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AtmListResponse(
    @JsonProperty("Data") DataBlock data,
    @JsonProperty("Links") LinksBlock links,
    @JsonProperty("Meta") MetaBlock meta
) {
    public record DataBlock(@JsonProperty("ATM") List<AtmItem> atms) {}

    public record AtmItem(
        @JsonProperty("AtmId") String atmId,
        @JsonProperty("Name") String name,
        @JsonProperty("Status") String status,
        @JsonProperty("Latitude") double latitude,
        @JsonProperty("Longitude") double longitude,
        @JsonProperty("Address") String address,
        @JsonProperty("City") String city,
        @JsonProperty("Country") String country,
        @JsonProperty("Accessibility") String accessibility,
        @JsonProperty("Services") List<String> services,
        @JsonProperty("UpdatedAt") String updatedAt
    ) {}

    public record LinksBlock(@JsonProperty("Self") String self) {}

    public record MetaBlock(@JsonProperty("TotalRecords") int totalRecords) {}
}
