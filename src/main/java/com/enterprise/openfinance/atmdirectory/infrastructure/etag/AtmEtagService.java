package com.enterprise.openfinance.atmdirectory.infrastructure.etag;

import com.enterprise.openfinance.atmdirectory.domain.Atm;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Component
public class AtmEtagService {

    public String compute(List<Atm> atms, Double lat, Double lon, Double radius) {
        String payload = safe(lat) + "|" + safe(lon) + "|" + safe(radius) + "|" +
                atms.stream()
                        .sorted(Comparator.comparing(Atm::atmId))
                        .map(atm -> String.join("|",
                                atm.atmId(),
                                atm.name(),
                                atm.status(),
                                String.valueOf(atm.latitude()),
                                String.valueOf(atm.longitude()),
                                atm.address(),
                                atm.city(),
                                atm.country(),
                                atm.accessibility(),
                                String.join(",", atm.services()),
                                atm.updatedAt() == null ? "" : atm.updatedAt().toString()))
                        .reduce((a, b) -> a + "#" + b)
                        .orElse("");
        return "\"" + sha256(payload) + "\"";
    }

    private String safe(Double value) {
        return value == null ? "" : value.toString();
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
