package com.buy01.user_service.security;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

/** Security filters run outside MVC advice but use the same public error contract. */
public final class ApiErrorWriter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ApiErrorWriter() { }

    public static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        JSON.writeValue(response.getOutputStream(), Map.of(
                "code", status.name(), "message", message, "details", Map.of(),
                "status", status.value(), "error", status.getReasonPhrase()));
    }
}
