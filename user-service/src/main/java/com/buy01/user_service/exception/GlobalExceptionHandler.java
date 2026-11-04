package com.buy01.user_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String INVALID_VALUE = "Invalid value";

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailExists(
            EmailAlreadyExistsException ex
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "Conflict",
                ex.getMessage()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(
            InvalidCredentialsException ex
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                ex.getMessage()
        );
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(
            UnauthorizedException ex
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                ex.getMessage()
        );
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            BadRequestException ex
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                ex.getMessage()
        );
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamService(
            UpstreamServiceException ex
    ) {
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                ex.getMessage()
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(
            UserNotFoundException ex
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Not Found",
                ex.getMessage()
        );
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Malformed JSON request or invalid field value"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex
    ) {
        Map<String, Object> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.isBindingFailure() || error.getDefaultMessage() == null ? INVALID_VALUE : error.getDefaultMessage()
                        )
                );

        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error ->
                        errors.put(
                                error.getObjectName(),
                                error.getDefaultMessage() == null ? INVALID_VALUE : error.getDefaultMessage()
                        )
                );

        String message = errors.values().stream()
                .map(Object::toString)
                .distinct()
                .reduce((a, b) -> a + ". " + b)
                .orElse("Validation failed");

        Map<String, Object> body = responseBody(HttpStatus.BAD_REQUEST, "Validation Failed", message);
        body.put("details", errors);
        body.put("fields", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(body);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(org.springframework.dao.DuplicateKeyException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", "An account with these details already exists");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException) {
            status = HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus resolved = HttpStatus.resolve(springError.getStatusCode().value());
            if (resolved != null) status = resolved;
        }
        return buildResponse(status, status.getReasonPhrase(), status.is5xxServerError()
                ? "An unexpected error occurred" : status.getReasonPhrase());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status,
            String error,
            String message
    ) {
        return ResponseEntity.status(status).body(responseBody(status, error, message));
    }

    private Map<String, Object> responseBody(HttpStatus status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "Validation Failed".equals(error) ? "VALIDATION_FAILED" : status.name());
        body.put("details", Map.of());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);

        return body;
    }
}