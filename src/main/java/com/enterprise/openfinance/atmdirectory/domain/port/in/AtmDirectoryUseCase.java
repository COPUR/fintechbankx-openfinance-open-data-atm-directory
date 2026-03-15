package com.enterprise.openfinance.atmdirectory.domain.port.in;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmListResult;
import com.enterprise.openfinance.atmdirectory.domain.query.ListAtmsQuery;

public interface AtmDirectoryUseCase {
    AtmListResult listAtms(ListAtmsQuery query);
}
