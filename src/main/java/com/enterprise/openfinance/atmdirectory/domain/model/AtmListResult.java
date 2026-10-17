package com.enterprise.openfinance.atmdirectory.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * ATMs answering a query and their content digest ({@link AtmContentDigest}), which the
 * web adapter serves as the ETag.
 */
public record AtmListResult(List<AtmLocation> atms, String contentDigest) {

    public AtmListResult {
        atms = List.copyOf(atms);
        Objects.requireNonNull(contentDigest, "contentDigest");
    }

    public AtmListResult(List<AtmLocation> atms) {
        this(atms, AtmContentDigest.of(atms));
    }
}
