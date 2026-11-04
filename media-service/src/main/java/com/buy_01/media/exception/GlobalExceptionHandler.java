package com.buy_01.media.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String INVALID_VALUE = "Invalid value";

    @ExceptionHandler(MediaStorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorage(MediaStorageException ex) {
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFile(InvalidFileException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "File size exceeds the maximum limit of 2 MB.");
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(MediaNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        Map<String, String> details = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> details.putIfAbsent(error.getField(),
                error.isBindingFailure() || error.getDefaultMessage() == null ? INVALID_VALUE : error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error -> details.putIfAbsent(error.getObjectName(),
                error.getDefaultMessage() == null ? INVALID_VALUE : error.getDefaultMessage()));
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid input data", "VALIDATION_FAILED", details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof org.springframework.http.converter.HttpMessageNotReadableException
                || ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException) {
            status = HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus resolved = HttpStatus.resolve(springError.getStatusCode().value());
            if (resolved != null) status = resolved;
        }
        return buildErrorResponse(status, status.is5xxServerError()
                ? "An unexpected error occurred" : status.getReasonPhrase());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        return buildErrorResponse(status, message, status.name(), Map.of());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message,
            String code, Map<String, String> details) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("details", details);
        body.put("timestamp", LocalDateTime.now(java.time.ZoneOffset.UTC));
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}

