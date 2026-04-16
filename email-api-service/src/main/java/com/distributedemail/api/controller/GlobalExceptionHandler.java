package com.distributedemail.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler - centralised error handler for all REST controllers.
 *
 * Converts exceptions into well-formed JSON error responses so the JavaFX
 * client always receives a consistent error body to display in the status bar.
 *
 * Handles:
 *   - Bean Validation failures (@Valid on request body)
 *   - IllegalArgumentException (bad request logic)
 *   - RuntimeException (internal errors)
 *   - Catch-all for any unexpected exception
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle validation errors from @Valid on @RequestBody parameters.
     * Returns a map of field names to validation error messages.
     *
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError
                ? ((FieldError) error).getField()
                : error.getObjectName();
            fieldErrors.put(field, error.getDefaultMessage());
        });

        Map<String, Object> body = buildErrorBody(
            HttpStatus.BAD_REQUEST.value(),
            "Validation failed",
            fieldErrors.toString()
        );

        log.warn("Validation error: {}", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handle bad request logic errors (e.g., task already SENT, invalid state).
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity
            .badRequest()
            .body(buildErrorBody(400, "Bad Request", ex.getMessage()));
    }

    /**
     * Handle domain-level runtime errors (e.g., entity not found).
     * HTTP 500 Internal Server Error
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime error: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(buildErrorBody(500, "Internal Server Error", ex.getMessage()));
    }

    /**
     * Catch-all handler for any unexpected exceptions.
     * HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(buildErrorBody(500, "Unexpected Error", ex.getMessage()));
    }

    private Map<String, Object> buildErrorBody(int status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        return body;
    }
}
