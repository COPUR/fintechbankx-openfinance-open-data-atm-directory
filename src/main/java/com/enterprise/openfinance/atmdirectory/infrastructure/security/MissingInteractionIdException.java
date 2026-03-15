package com.enterprise.openfinance.atmdirectory.infrastructure.security;

public class MissingInteractionIdException extends RuntimeException {
    public MissingInteractionIdException(String message) {
        super(message);
    }
}
