package com.enterprise.openfinance.atmdirectory.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.openfinance.atmdirectory.domain.exception.AtmDirectoryUnavailableException;
import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.model.GeoBoundingBox;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

class JpaAtmDirectoryAdapterTest {

    private static final AtmLocation DOWNTOWN = new AtmLocation("ATM-001", "Downtown", "InService", 25.2048, 55.2708,
        "Sheikh Zayed Rd", "Dubai", "AE", "Wheelchair", List.of("CashWithdrawal"), "AED",
        Instant.parse("2026-03-01T00:00:00Z"));

    private final SpringDataAtmRepository repository = mock(SpringDataAtmRepository.class);
    private final JpaAtmDirectoryAdapter adapter = new JpaAtmDirectoryAdapter(repository);

    @Test
    void findAllMapsRowsToTheDomain() {
        when(repository.findAllByOrderByAtmIdAsc()).thenReturn(List.of(AtmPersistenceMapper.toEntity(DOWNTOWN, 0)));

        assertThat(adapter.findAll()).containsExactly(DOWNTOWN);
    }

    @Test
    void findWithinPassesTheBoxCornersInOrder() {
        GeoBoundingBox box = new GeoBoundingBox(24.9, 25.4, 55.0, 55.5);
        when(repository.findInBox(24.9, 25.4, 55.0, 55.5)).thenReturn(List.of(AtmPersistenceMapper.toEntity(DOWNTOWN, 2)));

        assertThat(adapter.findWithin(box)).containsExactly(DOWNTOWN);
    }

    @Test
    void storeFailuresBecomeADomainUnavailableError() {
        when(repository.findAllByOrderByAtmIdAsc()).thenThrow(new DataAccessResourceFailureException("connection refused"));
        when(repository.findInBox(0, 1, 0, 1)).thenThrow(new CannotCreateTransactionException("pool exhausted"));

        assertThatThrownBy(adapter::findAll).isInstanceOf(AtmDirectoryUnavailableException.class)
            .hasCauseInstanceOf(DataAccessResourceFailureException.class);
        assertThatThrownBy(() -> adapter.findWithin(new GeoBoundingBox(0, 1, 0, 1)))
            .isInstanceOf(AtmDirectoryUnavailableException.class)
            .hasCauseInstanceOf(CannotCreateTransactionException.class);
    }
}
