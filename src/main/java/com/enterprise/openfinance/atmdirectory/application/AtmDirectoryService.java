package com.enterprise.openfinance.atmdirectory.application;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmListResult;
import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
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
        List<AtmLocation> all = atmDirectoryPort.findAll();
        if (query.latitude() == null || query.longitude() == null) {
            return new AtmListResult(all);
        }
        double radius = query.radiusKm() == null ? 10.0d : query.radiusKm();
        List<AtmLocation> filtered = all.stream()
            .filter(atm -> distanceKm(query.latitude(), query.longitude(), atm.latitude(), atm.longitude()) <= radius)
            .toList();
        return new AtmListResult(filtered);
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
