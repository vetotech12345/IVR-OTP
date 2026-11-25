package com.otpservice.web;

/** Every error has this shape: {@code {"error": {"code": ..., "message": ...}}}. */
public record ApiError(Body error) {

    public record Body(String code, String message) {
    }

    public static ApiError of(String code, String message) {
        return new ApiError(new Body(code, message));
    }
}
