package com.enterprise.openfinance.atmdirectory.infrastructure.persistence;

import com.enterprise.openfinance.atmdirectory.domain.exception.AtmDirectoryUnavailableException;
import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.port.out.AtmDirectoryPort;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

/** PostgreSQL-backed directory: the authority for ATM data (ADR-0001). */
@Component
public class JpaAtmDirectoryAdapter implements AtmDirectoryPort {

    private final SpringDataAtmRepository repository;

    JpaAtmDirectoryAdapter(SpringDataAtmRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AtmLocation> findAll() {
        return read(repository::findListedOrderByAtmId);
    }

    private static List<AtmLocation> read(Supplier<List<AtmJpaEntity>> query) {
        try {
            return query.get().stream().map(AtmPersistenceMapper::toDomain).toList();
        } catch (DataAccessException | TransactionException ex) {
            throw new AtmDirectoryUnavailableException("ATM directory store is unavailable", ex);
        }
    }
}
