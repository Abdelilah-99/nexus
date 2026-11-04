package com.buy01.product_service.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.buy01.product_service.dtos.response.ErrorResponse;
 
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String INVALID_VALUE = "Invalid value";
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrrors(MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> details.putIfAbsent(error.getField(),
                error.isBindingFailure() || error.getDefaultMessage() == null ? INVALID_VALUE : error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error -> details.putIfAbsent(error.getObjectName(),
                error.getDefaultMessage() == null ? INVALID_VALUE : error.getDefaultMessage()));
        ErrorResponse errResp = new ErrorResponse(LocalDateTime.now(java.time.ZoneOffset.UTC), HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Invalid input data",
                request.getRequestURI(), details);
        return new ResponseEntity<>(errResp, HttpStatus.BAD_REQUEST);

    }

    @ExceptionHandler(BadRequest.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequest ex,
            HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(java.time.ZoneOffset.UTC),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI());

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

 
    // handle resource not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(java.time.ZoneOffset.UTC),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI());

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex,
            HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(java.time.ZoneOffset.UTC),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage(),
                request.getRequestURI());

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex,
            HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(java.time.ZoneOffset.UTC),
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ex.getMessage(),
                request.getRequestURI());

        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

 

    // and hadi the last one is gonne be for all other exceptions that u cant put ur
    // hand on ;
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof org.springframework.http.converter.HttpMessageNotReadableException
                || ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException) {
            status = HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus resolved = HttpStatus.resolve(springError.getStatusCode().value());
            if (resolved != null) status = resolved;
        }
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(java.time.ZoneOffset.UTC), status.value(), status.getReasonPhrase(),
                status.is5xxServerError() ? "An unexpected error occurred" : status.getReasonPhrase(),
                request.getRequestURI());

        return new ResponseEntity<>(errorResponse, status);
    }

}
