package com.enterprise.openfinance.atmdirectory.infrastructure.web;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.port.in.AtmDirectoryUseCase;
import com.enterprise.openfinance.atmdirectory.domain.query.ListAtmsQuery;
import com.enterprise.openfinance.atmdirectory.infrastructure.web.dto.AtmListResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AtmDirectoryController {

    private final AtmDirectoryUseCase atmDirectoryUseCase;

    public AtmDirectoryController(AtmDirectoryUseCase atmDirectoryUseCase) {
        this.atmDirectoryUseCase = atmDirectoryUseCase;
    }

    @GetMapping("/open-finance/v1/atms")
    public ResponseEntity<AtmListResponse> listAtms(
        @RequestHeader("X-FAPI-Interaction-ID") String interactionId,
        @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
        @RequestParam(value = "lat", required = false) Double lat,
        @RequestParam(value = "long", required = false) Double lon,
        @RequestParam(value = "radius", required = false) Double radius,
        HttpServletRequest request
    ) {
        List<AtmLocation> atms = atmDirectoryUseCase.listAtms(new ListAtmsQuery(lat, lon, radius)).atms();
        String etag = toEtag(atms);

        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .header("X-FAPI-Interaction-ID", interactionId)
                .header("X-OF-Cache", "HIT")
                .header(HttpHeaders.ETAG, etag)
                .build();
        }

        AtmListResponse response = new AtmListResponse(
            new AtmListResponse.DataBlock(atms.stream().map(this::toItem).toList()),
            new AtmListResponse.LinksBlock(buildSelfLink(request)),
            new AtmListResponse.MetaBlock(atms.size())
        );

        return ResponseEntity.ok()
            .header("X-FAPI-Interaction-ID", interactionId)
            .header("X-OF-Cache", "MISS")
            .header(HttpHeaders.ETAG, etag)
            .body(response);
    }

    private AtmListResponse.AtmItem toItem(AtmLocation atm) {
        return new AtmListResponse.AtmItem(
            atm.atmId(),
            atm.name(),
            atm.status(),
            atm.latitude(),
            atm.longitude(),
            atm.address(),
            atm.city(),
            atm.country(),
            atm.accessibility(),
            atm.services(),
            atm.updatedAt().toString()
        );
    }

    private String buildSelfLink(HttpServletRequest request) {
        String base = request.getRequestURL().toString();
        String query = request.getQueryString();
        return query == null ? base : base + "?" + query;
    }

    static String toEtag(List<AtmLocation> atms) {
        String canonical = atms.stream()
            .map(a -> String.join("|",
                a.atmId(),
                a.name(),
                a.status(),
                String.valueOf(a.latitude()),
                String.valueOf(a.longitude()),
                a.address(),
                a.city(),
                a.country(),
                a.accessibility(),
                String.join(",", a.services()),
                a.updatedAt().toString()))
            .sorted()
            .reduce("", (x, y) -> x + ";" + y);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "\"" + HexFormat.of().formatHex(hash) + "\"";
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
