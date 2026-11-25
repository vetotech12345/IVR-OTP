package com.otpservice.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException failure) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(failure.getStatus());
        if (failure.getRetryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(failure.getRetryAfterSeconds()));
        }
        return response.body(ApiError.of(failure.getCode(), failure.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException failure) {
        String message = failure.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst().orElse("The request is not valid.");
        return ResponseEntity.unprocessableEntity().body(ApiError.of("VALIDATION_FAILED", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable() {
        return ResponseEntity.badRequest().body(ApiError.of("MALFORMED_REQUEST", "The request body is not valid JSON."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", "No such endpoint."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of("METHOD_NOT_ALLOWED", "That method is not allowed here."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception failure) {
        log.error("Unhandled failure", failure);
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL", "Something went wrong."));
    }
}
