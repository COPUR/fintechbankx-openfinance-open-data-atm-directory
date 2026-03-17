package com.enterprise.openfinance.atmdirectory.domain.port.out;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import java.util.List;

public interface AtmDirectoryPort {
    List<AtmLocation> findAll();
}
