package com.buy01.gateway.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Proxy/application failures must not expose internal addresses or stack traces. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus resolved = HttpStatus.resolve(springError.getStatusCode().value());
            if (resolved != null) status = resolved;
        } else if (ex instanceof org.springframework.web.client.RestClientException) {
            status = HttpStatus.BAD_GATEWAY;
        }
        String message = status.is5xxServerError() ? "Service temporarily unavailable. Please retry." : status.getReasonPhrase();
        return ResponseEntity.status(status).body(Map.of("code", status.name(), "message", message,
                "details", Map.of(), "status", status.value(), "error", status.getReasonPhrase()));
    }
}
