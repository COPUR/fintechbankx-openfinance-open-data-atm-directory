package com.enterprise.openfinance.atmdirectory.infrastructure.web;

import com.enterprise.openfinance.atmdirectory.application.port.in.ListAtmsUseCase;
import com.enterprise.openfinance.atmdirectory.domain.Atm;
import com.enterprise.openfinance.atmdirectory.infrastructure.etag.AtmEtagService;
import com.enterprise.openfinance.atmdirectory.infrastructure.web.dto.AtmListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/open-finance/v1")
public class AtmDirectoryController {

    private final ListAtmsUseCase listAtmsUseCase;
    private final AtmEtagService atmEtagService;

    public AtmDirectoryController(ListAtmsUseCase listAtmsUseCase, AtmEtagService atmEtagService) {
        this.listAtmsUseCase = listAtmsUseCase;
        this.atmEtagService = atmEtagService;
    }

    @GetMapping("/atms")
    ResponseEntity<AtmListResponse> listAtms(
            @RequestHeader("X-FAPI-Interaction-ID") String interactionId,
            @RequestParam(value = "lat", required = false) Double latitude,
            @RequestParam(value = "long", required = false) Double longitude,
            @RequestParam(value = "radius", required = false) Double radius,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        List<Atm> atms = listAtmsUseCase.list(latitude, longitude, radius);
        String etag = atmEtagService.compute(atms, latitude, longitude, radius);
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .header("X-FAPI-Interaction-ID", interactionId)
                    .header("X-OF-Cache", "HIT")
                    .build();
        }

        List<AtmListResponse.AtmItem> items = atms.stream()
                .map(atm -> new AtmListResponse.AtmItem(
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
                        atm.updatedAt() == null ? null : atm.updatedAt().toString()))
                .toList();

        AtmListResponse body = new AtmListResponse(
                new AtmListResponse.Data(items),
                new AtmListResponse.Links("/open-finance/v1/atms"),
                new AtmListResponse.Meta(items.size())
        );

        return ResponseEntity.ok()
                .eTag(etag)
                .header("X-FAPI-Interaction-ID", interactionId)
                .header("X-OF-Cache", "MISS")
                .body(body);
    }
}
