package com.enterprise.openfinance.atmdirectory.domain.port.out;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import java.util.List;

/**
 * Read access to the listed ATM directory. Implementations leave out ATMs whose
 * status is {@link AtmLocation#STATUS_WITHDRAWN}, order results by atmId and
 * throw {@link com.enterprise.openfinance.atmdirectory.domain.exception.AtmDirectoryUnavailableException}
 * when the store cannot be read.
 */
public interface AtmDirectoryPort {

    /** Every listed ATM; the application keeps it as an in-process snapshot. */
    List<AtmLocation> findAll();
}
