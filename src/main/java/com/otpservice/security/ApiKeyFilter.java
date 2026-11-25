package com.otpservice.security;

import com.otpservice.app.ApiKey;
import com.otpservice.app.ApiKeyRepository;
import com.otpservice.app.ClientApp;
import com.otpservice.app.ClientAppRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code /v1/**}: the {@code X-Api-Key} header decides which app this request is for.
 *
 * <p>Every failure - missing, malformed, unknown, revoked, or an inactive app - gets the same 401 and the same
 * sentence, so the response says nothing about which keys exist.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";
    /** {@code last_used_at} is written at most this often per key, not on every request. */
    private static final Duration TOUCH_EVERY = Duration.ofMinutes(1);

    private final ApiKeyRepository keys;
    private final ClientAppRepository apps;

    public ApiKeyFilter(ApiKeyRepository keys, ClientAppRepository apps) {
        this.keys = keys;
        this.apps = apps;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<ApiKey> key = authenticate(request.getHeader(HEADER));
        Optional<ClientApp> app = key.flatMap(found -> apps.findById(found.getAppId())).filter(ClientApp::isActive);
        if (app.isEmpty()) {
            JsonErrors.write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED",
                    "Send a valid API key in the " + HEADER + " header.");
            return;
        }
        touch(key.get());
        request.setAttribute(CurrentApp.ATTRIBUTE, app.get());
        request.setAttribute(CurrentApp.KEY_ATTRIBUTE, key.get().getId());
        chain.doFilter(request, response);
    }

    private Optional<ApiKey> authenticate(String header) {
        Optional<ApiKeys.Presented> presented = ApiKeys.parse(header);
        if (presented.isEmpty()) {
            return Optional.empty();
        }
        return keys.findByPrefix(presented.get().prefix())
                .filter(found -> !found.isRevoked())
                .filter(found -> ApiKeys.matches(presented.get(), found.getSecretHash()));
    }

    private void touch(ApiKey key) {
        Instant now = Instant.now();
        if (key.getLastUsedAt() == null || key.getLastUsedAt().isBefore(now.minus(TOUCH_EVERY))) {
            key.setLastUsedAt(now);
            keys.save(key);
        }
    }
}
