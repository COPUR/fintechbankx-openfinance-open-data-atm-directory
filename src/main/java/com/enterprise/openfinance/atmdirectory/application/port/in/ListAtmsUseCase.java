package com.enterprise.openfinance.atmdirectory.application.port.in;

import com.enterprise.openfinance.atmdirectory.domain.Atm;

import java.util.List;

public interface ListAtmsUseCase {
    List<Atm> list(Double latitude, Double longitude, Double radiusKm);
}
