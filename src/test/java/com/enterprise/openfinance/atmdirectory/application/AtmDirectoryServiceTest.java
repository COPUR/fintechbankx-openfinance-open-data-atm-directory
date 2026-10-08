package com.enterprise.openfinance.atmdirectory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.openfinance.atmdirectory.domain.exception.AtmDirectoryUnavailableException;
import com.enterprise.openfinance.atmdirectory.domain.model.AtmContentDigest;
import com.enterprise.openfinance.atmdirectory.domain.model.AtmListResult;
import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.port.out.AtmDirectoryPort;
import com.enterprise.openfinance.atmdirectory.domain.query.ListAtmsQuery;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AtmDirectoryServiceTest {

    private static final AtmLocation DOWNTOWN = atm("A", 25.2048, 55.2708, "Dubai");
    private static final AtmLocation MARINA = atm("B", 25.0800, 55.1400, "Dubai");
    private static final AtmLocation ABU_DHABI = atm("C", 24.4950, 54.3820, "Abu Dhabi");
    private static final Duration MAX_AGE = Duration.ofMinutes(10);

    @Mock
    private AtmDirectoryPort atmDirectoryPort;

    private MutableClock clock;
    private AtmDirectoryService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-03-10T08:00:00Z"));
        service = new AtmDirectoryService(atmDirectoryPort, clock, MAX_AGE);
    }

    @Test
    void repeatedReadsAndRevalidationsAreServedFromTheSnapshot() {
        when(atmDirectoryPort.findAll()).thenReturn(List.of(DOWNTOWN, MARINA, ABU_DHABI));

        service.listAtms(new ListAtmsQuery(null, null, null));
        service.listAtms(new ListAtmsQuery(null, null, null));
        service.listAtms(new ListAtmsQuery(25.2048, 55.2708, 25.0));
        clock.advance(Duration.ofMinutes(9));
        service.listAtms(new ListAtmsQuery(null, null, null));

        verify(atmDirectoryPort, times(1)).findAll();
    }

    @Test
    void fullListingCarriesTheDigestComputedWhenTheSnapshotWasLoaded() {
        when(atmDirectoryPort.findAll()).thenReturn(List.of(DOWNTOWN, MARINA, ABU_DHABI));

        AtmListResult result = service.listAtms(new ListAtmsQuery(null, null, null));

        assertThat(result.atms()).extracting(AtmLocation::atmId).containsExactly("A", "B", "C");
        assertThat(result.contentDigest()).isEqualTo(AtmContentDigest.of(List.of(DOWNTOWN, MARINA, ABU_DHABI)));
    }

    @Test
    void geoQueriesFilterTheSnapshotByGreatCircleDistance() {
        // Marina is 19.13 km from downtown: inside a 20 km radius, outside a 19 km one.
        when(atmDirectoryPort.findAll()).thenReturn(List.of(DOWNTOWN, MARINA, ABU_DHABI));

        AtmListResult twenty = service.listAtms(new ListAtmsQuery(25.2048, 55.2708, 20.0));
        assertThat(twenty.atms()).extracting(AtmLocation::atmId).containsExactly("A", "B");
        assertThat(twenty.contentDigest()).isEqualTo(AtmContentDigest.of(List.of(DOWNTOWN, MARINA)));
        assertThat(service.listAtms(new ListAtmsQuery(25.2048, 55.2708, 19.0)).atms())
            .extracting(AtmLocation::atmId).containsExactly("A");
    }

    @Test
    void radiusDefaultsToFiveKilometres() {
        // 3.00 km and 7.01 km due north of downtown: only the first is inside the 5 km default.
        AtmLocation threeKm = atm("N3", 25.2318, 55.2708, "Dubai");
        AtmLocation sevenKm = atm("N7", 25.2678, 55.2708, "Dubai");
        when(atmDirectoryPort.findAll()).thenReturn(List.of(DOWNTOWN, threeKm, sevenKm));

        assertThat(service.listAtms(new ListAtmsQuery(25.2048, 55.2708, null)).atms())
            .extracting(AtmLocation::atmId).containsExactly("A", "N3");
    }

    @Test
    void refreshPicksUpAnImport() {
        when(atmDirectoryPort.findAll()).thenReturn(List.of(DOWNTOWN), List.of(DOWNTOWN, MARINA));

        assertThat(service.listAtms(new ListAtmsQuery(null, null, null)).atms()).hasSize(1);
        service.refresh();

        assertThat(service.listAtms(new ListAtmsQuery(null, null, null)).atms()).hasSize(2);
    }

    @Test
    void failedRefreshKeepsServingTheLastSnapshotWithinTheMaximumAge() {
        when(atmDirectoryPort.findAll()).thenReturn(List.of(DOWNTOWN, MARINA))
            .thenThrow(new AtmDirectoryUnavailableException("store down", null));
        service.refresh();
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(service::refresh).isInstanceOf(AtmDirectoryUnavailableException.class);
        assertThat(service.listAtms(new ListAtmsQuery(null, null, null)).atms()).hasSize(2);
        assertThat(service.snapshotLoadedAt()).contains(Instant.parse("2026-03-10T08:00:00Z"));
    }

    @Test
    void snapshotOlderThanTheMaximumAgeIsReloadedAndTheStoreOutageSurfaces() {
        when(atmDirectoryPort.findAll()).thenReturn(List.of(DOWNTOWN, MARINA))
            .thenThrow(new AtmDirectoryUnavailableException("store down", null));
        service.refresh();
        clock.advance(MAX_AGE.plusSeconds(1));

        assertThatThrownBy(() -> service.listAtms(new ListAtmsQuery(null, null, null)))
            .isInstanceOf(AtmDirectoryUnavailableException.class);
    }

    @Test
    void firstRequestLoadsTheSnapshotWhenNoRefreshHasRunYet() {
        when(atmDirectoryPort.findAll()).thenReturn(List.of(MARINA));

        assertThat(service.snapshotLoadedAt()).isEmpty();
        assertThat(service.listAtms(new ListAtmsQuery(null, null, null)).atms()).containsExactly(MARINA);
        assertThat(service.snapshotLoadedAt()).isPresent();
    }

    @Test
    void greatCircleDistanceMatchesKnownFigures() {
        assertThat(AtmDirectoryService.distanceKm(25.2048, 55.2708, 25.0800, 55.1400)).isCloseTo(19.129, within(0.001));
        assertThat(AtmDirectoryService.distanceKm(25.2048, 55.2708, 24.4950, 54.3820)).isCloseTo(119.464, within(0.001));
    }

    private static AtmLocation atm(String id, double lat, double lon, String city) {
        return new AtmLocation(id, "ATM " + id, "InService", lat, lon, "Road", city, "AE", "Wheelchair",
            List.of("CashWithdrawal"), "AED", Instant.parse("2026-03-01T00:00:00Z"));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
