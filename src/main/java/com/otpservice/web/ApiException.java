package com.otpservice.web;

import org.springframework.http.HttpStatus;

/** A refusal the caller can act on: a status, a stable code, a sentence, and when to retry if relevant. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Integer retryAfterSeconds;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Integer retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message);
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " was not found.");
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static ApiException rateLimited(String message, int retryAfterSeconds) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message, retryAfterSeconds);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
}
