package com.enterprise.openfinance.atmdirectory.application;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmListResult;
import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.model.Coordinates;
import com.enterprise.openfinance.atmdirectory.domain.model.GeoBoundingBox;
import com.enterprise.openfinance.atmdirectory.domain.port.in.AtmDirectoryUseCase;
import com.enterprise.openfinance.atmdirectory.domain.port.out.AtmDirectoryPort;
import com.enterprise.openfinance.atmdirectory.domain.query.ListAtmsQuery;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AtmDirectoryService implements AtmDirectoryUseCase {

    private final AtmDirectoryPort atmDirectoryPort;

    public AtmDirectoryService(AtmDirectoryPort atmDirectoryPort) {
        this.atmDirectoryPort = atmDirectoryPort;
    }

    @Override
    public AtmListResult listAtms(ListAtmsQuery query) {
        if (!query.hasGeoFilter()) {
            return new AtmListResult(atmDirectoryPort.findAll());
        }
        double radius = query.effectiveRadiusKm();
        GeoBoundingBox box = GeoBoundingBox.around(query.latitude(), query.longitude(), radius);
        List<AtmLocation> nearby = atmDirectoryPort.findWithin(box).stream()
            .filter(atm -> distanceKm(query.latitude(), query.longitude(), atm.latitude(), atm.longitude()) <= radius)
            .toList();
        return new AtmListResult(nearby);
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
