package com.enterprise.openfinance.atmdirectory.support;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.port.out.AtmDirectoryPort;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Test double for the directory port, holding the same sample ATMs as db/seed. */
public class InMemoryAtmDirectoryAdapter implements AtmDirectoryPort {

    private final List<AtmLocation> atms;

    public InMemoryAtmDirectoryAdapter() {
        this(List.of(
            new AtmLocation("ATM-001", "Downtown Branch ATM", "InService", 25.2048, 55.2708,
                "Sheikh Zayed Rd", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal", "CashDeposit"), "AED",
                Instant.parse("2026-03-01T00:00:00Z")),
            new AtmLocation("ATM-002", "Marina ATM", "InService", 25.0800, 55.1400,
                "Dubai Marina Walk", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), "AED",
                Instant.parse("2026-03-02T00:00:00Z")),
            new AtmLocation("ATM-003", "Abu Dhabi Mall ATM", "OutOfService", 24.4950, 54.3820,
                "Abu Dhabi Mall", "Abu Dhabi", "AE", "Standard", List.of("CashWithdrawal"), "AED",
                Instant.parse("2026-03-03T00:00:00Z"))));
    }

    public InMemoryAtmDirectoryAdapter(List<AtmLocation> atms) {
        this.atms = atms.stream()
            .filter(atm -> !AtmLocation.STATUS_WITHDRAWN.equals(atm.status()))
            .sorted(Comparator.comparing(AtmLocation::atmId)).toList();
    }

    @Override
    public List<AtmLocation> findAll() {
        return atms;
    }
}
