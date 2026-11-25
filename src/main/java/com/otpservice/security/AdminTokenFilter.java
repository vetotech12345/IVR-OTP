package com.otpservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/** {@code /admin/**}: {@code Authorization: Bearer <OTP_ADMIN_TOKEN>}, compared in constant time. */
public class AdminTokenFilter extends OncePerRequestFilter {

    private final byte[] token;

    public AdminTokenFilter(String token) {
        // An empty token would match an empty header. StartupChecks refuses to start without one; this makes
        // the filter safe on its own, too.
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("OTP_ADMIN_TOKEN is not set.");
        }
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String presented = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
        if (!MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), token)) {
            JsonErrors.write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED",
                    "Send the admin token as Authorization: Bearer <token>.");
            return;
        }
        chain.doFilter(request, response);
    }
}
