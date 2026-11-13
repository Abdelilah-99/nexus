package com.neo4flix.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** The one JSON error format returned by every Neo4flix service. */
public record ApiError(Instant timestamp, int status, String error, String message, List<String> details) {

    public static ApiError of(HttpStatus status, String message, List<String> details) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, details);
    }

    /** Used by the security filters, which run before Spring MVC and its JSON converters. */
    static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\"" + message + "\",\"details\":[]}");
    }
}
