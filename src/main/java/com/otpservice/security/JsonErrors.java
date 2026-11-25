package com.otpservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otpservice.web.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Filters answer before any controller exists, so they write the error shape themselves. */
final class JsonErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonErrors() {
    }

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        MAPPER.writeValue(response.getOutputStream(), ApiError.of(code, message));
    }
}
