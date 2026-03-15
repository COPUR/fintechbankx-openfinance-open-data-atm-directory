package com.enterprise.openfinance.atmdirectory.infrastructure.web;

import com.enterprise.openfinance.atmdirectory.infrastructure.security.MissingInteractionIdException;
import com.enterprise.openfinance.atmdirectory.infrastructure.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MissingInteractionIdException.class)
    ResponseEntity<ErrorResponse> handleMissingInteraction(MissingInteractionIdException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "HEADER_MISSING",
                        exception.getMessage(),
                        "N/A",
                        OffsetDateTime.now().toString()
                ));
    }
}
