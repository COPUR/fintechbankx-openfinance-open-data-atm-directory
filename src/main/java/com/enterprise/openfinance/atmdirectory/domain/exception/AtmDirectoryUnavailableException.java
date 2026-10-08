package com.enterprise.openfinance.atmdirectory.domain.exception;

/** The directory store could not be read; the request may be retried. */
public class AtmDirectoryUnavailableException extends RuntimeException {

    public AtmDirectoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
