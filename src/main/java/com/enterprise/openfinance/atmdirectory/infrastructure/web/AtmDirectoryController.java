package com.enterprise.openfinance.atmdirectory.infrastructure.web;

import com.enterprise.openfinance.atmdirectory.domain.model.AtmLocation;
import com.enterprise.openfinance.atmdirectory.domain.port.in.AtmDirectoryUseCase;
import com.enterprise.openfinance.atmdirectory.domain.query.ListAtmsQuery;
import com.enterprise.openfinance.atmdirectory.infrastructure.web.dto.AtmListResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AtmDirectoryController {

    static final String PATH = "/open-finance/v1/atms";

    /**
     * Every cache, shared or private, must revalidate with the ETag before reuse. A
     * response served from a cache without revalidation would replay another caller's
     * X-FAPI-Interaction-ID; a 304 from this service carries the caller's own.
     */
    private static final CacheControl REVALIDATE = CacheControl.noCache();

    private final AtmDirectoryUseCase atmDirectoryUseCase;

    public AtmDirectoryController(AtmDirectoryUseCase atmDirectoryUseCase) {
        this.atmDirectoryUseCase = atmDirectoryUseCase;
    }

    @GetMapping(PATH)
    public ResponseEntity<AtmListResponse> listAtms(
        @RequestHeader("X-FAPI-Interaction-ID") String interactionId,
        @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "lat", required = false) Double lat,
        @RequestParam(value = "long", required = false) Double lon,
        @RequestParam(value = "radius", required = false) Double radius
    ) {
        requireInteractionId(interactionId);
        requireSupportedAuthorizationScheme(authorization);
        List<AtmLocation> atms = atmDirectoryUseCase.listAtms(new ListAtmsQuery(lat, lon, radius)).atms();
        String etag = toEtag(atms);

        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .header("X-FAPI-Interaction-ID", interactionId)
                .header("X-OF-Cache", "HIT")
                .eTag(etag)
                .cacheControl(REVALIDATE)
                .build();
        }

        AtmListResponse response = new AtmListResponse(
            new AtmListResponse.DataBlock(atms.stream().map(this::toItem).toList()),
            new AtmListResponse.LinksBlock(selfLink(lat, lon, radius)),
            new AtmListResponse.MetaBlock(atms.size())
        );

        return ResponseEntity.ok()
            .header("X-FAPI-Interaction-ID", interactionId)
            .header("X-OF-Cache", "MISS")
            .eTag(etag)
            .cacheControl(REVALIDATE)
            .body(response);
    }

    private static void requireInteractionId(String interactionId) {
        if (interactionId.isBlank()) {
            throw new IllegalArgumentException("X-FAPI-Interaction-ID must not be blank");
        }
    }

    /** Public endpoint: no token is needed, but a token that is sent must be Bearer or DPoP (monolith parity). */
    private static void requireSupportedAuthorizationScheme(String authorization) {
        if (authorization != null && !authorization.isBlank()
            && !authorization.startsWith("Bearer ") && !authorization.startsWith("DPoP ")) {
            throw new IllegalArgumentException("Authorization header must use Bearer or DPoP token type");
        }
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
            atm.currency(),
            atm.updatedAt().toString()
        );
    }

    /**
     * Relative link rebuilt from the validated parameters, as the monolith did. Never
     * derived from the request URL, so Host / X-Forwarded-Host cannot reach the body.
     */
    static String selfLink(Double lat, Double lon, Double radius) {
        StringJoiner query = new StringJoiner("&", "?", "").setEmptyValue("");
        if (lat != null) {
            query.add("lat=" + lat);
        }
        if (lon != null) {
            query.add("long=" + lon);
        }
        if (radius != null) {
            query.add("radius=" + radius);
        }
        return PATH + query;
    }

    /**
     * Content ETag: SHA-256 over ";" + row for every ATM's canonical row, rows sorted, in one
     * digest pass (linear in the network size; the value is the same as the first release's).
     */
    static String toEtag(List<AtmLocation> atms) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
        byte[] separator = ";".getBytes(StandardCharsets.UTF_8);
        atms.stream().map(AtmDirectoryController::canonicalRow).sorted().forEach(row -> {
            digest.update(separator);
            digest.update(row.getBytes(StandardCharsets.UTF_8));
        });
        return "\"" + HexFormat.of().formatHex(digest.digest()) + "\"";
    }

    private static String canonicalRow(AtmLocation a) {
        return String.join("|",
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
            a.currency(),
            a.updatedAt().toString());
    }
}
