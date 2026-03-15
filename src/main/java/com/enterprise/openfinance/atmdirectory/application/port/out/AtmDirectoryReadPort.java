package com.enterprise.openfinance.atmdirectory.application.port.out;

import com.enterprise.openfinance.atmdirectory.domain.Atm;

import java.util.List;

public interface AtmDirectoryReadPort {
    List<Atm> findAll();
}
