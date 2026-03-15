package com.enterprise.openfinance.atmdirectory.application;

import com.enterprise.openfinance.atmdirectory.application.port.in.ListAtmsUseCase;
import com.enterprise.openfinance.atmdirectory.application.port.out.AtmDirectoryReadPort;
import com.enterprise.openfinance.atmdirectory.domain.Atm;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListAtmsService implements ListAtmsUseCase {

    private final AtmDirectoryReadPort readPort;

    public ListAtmsService(AtmDirectoryReadPort readPort) {
        this.readPort = readPort;
    }

    @Override
    public List<Atm> list(Double latitude, Double longitude, Double radiusKm) {
        if (latitude == null || longitude == null || radiusKm == null) {
            return readPort.findAll();
        }
        return readPort.findAll().stream()
                .filter(atm -> distanceKm(latitude, longitude, atm.latitude(), atm.longitude()) <= radiusKm)
                .toList();
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
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
