package com.otpservice.security;

import com.otpservice.app.ClientApp;
import jakarta.servlet.http.HttpServletRequest;

/** The app whose key authenticated this request. Set by {@link ApiKeyFilter}, read by controllers. */
public final class CurrentApp {

    static final String ATTRIBUTE = CurrentApp.class.getName();
    static final String KEY_ATTRIBUTE = CurrentApp.class.getName() + ".key";

    private CurrentApp() {
    }

    public static ClientApp of(HttpServletRequest request) {
        Object app = request.getAttribute(ATTRIBUTE);
        if (!(app instanceof ClientApp found)) {
            throw new IllegalStateException("No authenticated app on this request; is ApiKeyFilter registered?");
        }
        return found;
    }

    /** The id of the key that authenticated this request, recorded on every code it asks for. */
    public static java.util.UUID keyId(HttpServletRequest request) {
        return (java.util.UUID) request.getAttribute(KEY_ATTRIBUTE);
    }
}
