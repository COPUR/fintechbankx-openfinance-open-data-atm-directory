package com.enterprise.openfinance.atmdirectory.infrastructure.web.dto;

public record ErrorResponse(String code, String message, String interactionId, String timestamp) {
}
