package com.enterprise.openfinance.atmdirectory.infrastructure.web.dto;

import java.util.List;

public record AtmListResponse(Data Data, Links Links, Meta Meta) {
    public record Data(List<AtmItem> ATM) {
    }

    public record Links(String Self) {
    }

    public record Meta(int TotalRecords) {
    }

    public record AtmItem(
            String AtmId,
            String Name,
            String Status,
            double Latitude,
            double Longitude,
            String Address,
            String City,
            String Country,
            String Accessibility,
            List<String> Services,
            String UpdatedAt
    ) {
    }
}
