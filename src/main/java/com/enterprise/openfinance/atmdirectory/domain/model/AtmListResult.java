package com.enterprise.openfinance.atmdirectory.domain.model;

import java.util.List;

public record AtmListResult(List<AtmLocation> atms) {
    public AtmListResult {
        atms = List.copyOf(atms);
    }
}
