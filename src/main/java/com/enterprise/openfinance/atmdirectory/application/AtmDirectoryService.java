package com.enterprise.openfinance.atmdirectory.application;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmContentDigest;
import com.enterprise.openfinance.atmdirectory.domain.model.AtmListResult;
import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.model.Coordinates;
import com.enterprise.openfinance.atmdirectory.domain.model.GeoBoundingBox;
import com.enterprise.openfinance.atmdirectory.domain.port.in.AtmDirectoryUseCase;
import com.enterprise.openfinance.atmdirectory.domain.port.out.AtmDirectoryPort;
import com.enterprise.openfinance.atmdirectory.domain.query.ListAtmsQuery;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Serves the directory from an in-process snapshot, so reads and ETag revalidations
 * (304) do not touch PostgreSQL. {@link #refresh()} reloads the snapshot on a schedule;
 * if refreshing fails the last snapshot is served until it is older than {@code maxAge},
 * after which a request reloads it itself and a store outage surfaces as
 * {@link com.enterprise.openfinance.atmdirectory.domain.exception.AtmDirectoryUnavailableException}.
 */
public class AtmDirectoryService implements AtmDirectoryUseCase {

    private record Snapshot(AtmListResult all, Instant loadedAt) {
    }

    private final AtmDirectoryPort atmDirectoryPort;
    private final Clock clock;
    private final Duration maxAge;
    private volatile Snapshot snapshot;

    public AtmDirectoryService(AtmDirectoryPort atmDirectoryPort, Clock clock, Duration maxAge) {
        this.atmDirectoryPort = atmDirectoryPort;
        this.clock = clock;
        this.maxAge = maxAge;
    }

    @Override
    public AtmListResult listAtms(ListAtmsQuery query) {
        AtmListResult all = current().all();
        if (!query.hasGeoFilter()) {
            return all;
        }
        double radius = query.effectiveRadiusKm();
        GeoBoundingBox box = GeoBoundingBox.around(query.latitude(), query.longitude(), radius);
        List<AtmLocation> nearby = all.atms().stream()
            .filter(atm -> box.contains(atm.latitude(), atm.longitude()))
            .filter(atm -> distanceKm(query.latitude(), query.longitude(), atm.latitude(), atm.longitude()) <= radius)
            .toList();
        return new AtmListResult(nearby);
    }

    /** Reloads the snapshot from the store; on failure the previous snapshot stays in place. */
    public synchronized void refresh() {
        List<AtmLocation> atms = atmDirectoryPort.findAll();
        snapshot = new Snapshot(new AtmListResult(atms, AtmContentDigest.of(atms)), clock.instant());
    }

    public Optional<Instant> snapshotLoadedAt() {
        Snapshot current = snapshot;
        return current == null ? Optional.empty() : Optional.of(current.loadedAt());
    }

    private Snapshot current() {
        Snapshot current = snapshot;
        if (current == null || isTooOld(current)) {
            synchronized (this) {
                current = snapshot;
                if (current == null || isTooOld(current)) {
                    refresh();
                    current = snapshot;
                }
            }
        }
        return current;
    }

    private boolean isTooOld(Snapshot s) {
        return Duration.between(s.loadedAt(), clock.instant()).compareTo(maxAge) > 0;
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Coordinates.EARTH_RADIUS_KM * c;
    }
}
