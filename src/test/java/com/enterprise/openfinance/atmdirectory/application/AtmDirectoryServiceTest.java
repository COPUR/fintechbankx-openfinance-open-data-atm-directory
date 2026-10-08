package com.enterprise.openfinance.atmdirectory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.model.GeoBoundingBox;
import com.enterprise.openfinance.atmdirectory.domain.port.out.AtmDirectoryPort;
import com.enterprise.openfinance.atmdirectory.domain.query.ListAtmsQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AtmDirectoryServiceTest {

    private static final AtmLocation DOWNTOWN = atm("A", 25.2048, 55.2708, "Dubai");
    private static final AtmLocation MARINA = atm("B", 25.0800, 55.1400, "Dubai");
    private static final AtmLocation ABU_DHABI = atm("C", 24.4950, 54.3820, "Abu Dhabi");

    @Mock
    private AtmDirectoryPort atmDirectoryPort;

    private AtmDirectoryService service;

    @BeforeEach
    void setUp() {
        service = new AtmDirectoryService(atmDirectoryPort);
    }

    @Test
    void geoQueryAsksThePortForTheBoundingBoxOnly() {
        when(atmDirectoryPort.findWithin(any())).thenReturn(List.of(DOWNTOWN, MARINA));

        service.listAtms(new ListAtmsQuery(25.2048, 55.2708, 25.0));

        ArgumentCaptor<GeoBoundingBox> box = ArgumentCaptor.forClass(GeoBoundingBox.class);
        verify(atmDirectoryPort).findWithin(box.capture());
        verify(atmDirectoryPort, never()).findAll();
        assertThat(box.getValue().minLatitude()).isCloseTo(24.97997, within(1e-5));
        assertThat(box.getValue().maxLongitude()).isCloseTo(55.51929, within(1e-5));
    }

    @Test
    void boxCornersOutsideTheRadiusAreFilteredByGreatCircleDistance() {
        // Marina is 19.13 km from downtown: inside a 20 km radius, outside a 19 km one.
        when(atmDirectoryPort.findWithin(any())).thenReturn(List.of(DOWNTOWN, MARINA));

        assertThat(service.listAtms(new ListAtmsQuery(25.2048, 55.2708, 20.0)).atms())
            .extracting(AtmLocation::atmId).containsExactly("A", "B");
        assertThat(service.listAtms(new ListAtmsQuery(25.2048, 55.2708, 19.0)).atms())
            .extracting(AtmLocation::atmId).containsExactly("A");
    }

    @Test
    void radiusDefaultsToTenKilometres() {
        when(atmDirectoryPort.findWithin(any())).thenReturn(List.of(DOWNTOWN, MARINA));

        assertThat(service.listAtms(new ListAtmsQuery(25.2048, 55.2708, null)).atms())
            .extracting(AtmLocation::atmId).containsExactly("A");
    }

    @Test
    void shouldReturnAllAtmsWhenNoGeoFilter() {
        when(atmDirectoryPort.findAll()).thenReturn(List.of(DOWNTOWN, MARINA, ABU_DHABI));

        var result = service.listAtms(new ListAtmsQuery(null, null, null));

        assertThat(result.atms()).hasSize(3);
        verify(atmDirectoryPort, never()).findWithin(any());
    }

    @Test
    void latitudeWithoutLongitudeIsNotAGeoQuery() {
        when(atmDirectoryPort.findAll()).thenReturn(List.of(DOWNTOWN, ABU_DHABI));

        assertThat(service.listAtms(new ListAtmsQuery(25.2048, null, 5.0)).atms()).hasSize(2);
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
}
