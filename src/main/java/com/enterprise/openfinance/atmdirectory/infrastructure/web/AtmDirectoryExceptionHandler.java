package com.enterprise.openfinance.atmdirectory.infrastructure.web;

import com.enterprise.openfinance.atmdirectory.domain.exception.AtmDirectoryUnavailableException;
import com.enterprise.openfinance.atmdirectory.infrastructure.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class AtmDirectoryExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AtmDirectoryExceptionHandler.class);

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return badRequest(ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return badRequest("Query parameter '" + ex.getName() + "' has an invalid value", request);
    }

    /** Query rules from the domain (coordinate range, negative radius). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQuery(IllegalArgumentException ex, HttpServletRequest request) {
        return badRequest(ex.getMessage(), request);
    }

    @ExceptionHandler(AtmDirectoryUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(AtmDirectoryUnavailableException ex, HttpServletRequest request) {
        LOG.warn("ATM directory unavailable: {}", ex.getCause() == null ? ex.getMessage() : ex.getCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, "5")
            .body(new ErrorResponse("SERVICE_UNAVAILABLE", "ATM directory is temporarily unavailable",
                interactionId(request), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        LOG.error("Unhandled error serving the ATM directory", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
            "INTERNAL_ERROR",
            "Internal server error",
            interactionId(request),
            Instant.now()
        ));
    }

    private ResponseEntity<ErrorResponse> badRequest(String message, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", message, interactionId(request), Instant.now()));
    }

    private String interactionId(HttpServletRequest request) {
        String interactionId = request.getHeader("X-FAPI-Interaction-ID");
        return interactionId == null || interactionId.isBlank() ? "UNKNOWN" : interactionId;
    }
}
