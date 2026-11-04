package com.buy01.product_service.dtos.response;

import java.time.LocalDateTime;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors)

{
    public ErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null);
    }
    /** Stable application contract; legacy metadata remains during rollout. */
    @JsonProperty
    public String code() {
        if ("Validation Failed".equals(error)) {
            return "VALIDATION_FAILED";
        }
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved == null ? "HTTP_ERROR" : resolved.name();
    }

    @JsonProperty
    public Map<String, String> details() {
        return validationErrors == null ? Map.of() : Map.copyOf(validationErrors);
    }

}
