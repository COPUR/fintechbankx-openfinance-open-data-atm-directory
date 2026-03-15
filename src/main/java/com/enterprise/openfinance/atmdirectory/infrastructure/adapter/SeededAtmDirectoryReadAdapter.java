package com.enterprise.openfinance.atmdirectory.infrastructure.adapter;

import com.enterprise.openfinance.atmdirectory.application.port.out.AtmDirectoryReadPort;
import com.enterprise.openfinance.atmdirectory.domain.Atm;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class SeededAtmDirectoryReadAdapter implements AtmDirectoryReadPort {

    @Override
    public List<Atm> findAll() {
        return List.of(
                new Atm("ATM-DXB-001", "Downtown ATM", "InService", 25.2048, 55.2708, "Sheikh Zayed Rd", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal", "CashDeposit"), OffsetDateTime.parse("2026-03-01T00:00:00Z")),
                new Atm("ATM-AUH-001", "Corniche ATM", "InService", 24.4539, 54.3773, "Corniche St", "Abu Dhabi", "AE", "Wheelchair", List.of("CashWithdrawal"), OffsetDateTime.parse("2026-03-01T00:00:00Z"))
        );
    }
}
