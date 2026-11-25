package com.otpservice.config;

import org.springframework.stereotype.Component;

/**
 * Refuses to start with a weak or missing secret, or with the log "provider" switched on by accident.
 * A service that starts insecure is worse than one that does not start.
 *
 * <p><strong>In the constructor, not an {@code ApplicationRunner}.</strong> A runner runs after the web server
 * has started listening, so for a moment the service would answer with the checks not yet made. Singletons are
 * built before the server starts, so a failure here means it never listens at all.
 */
@Component
class StartupChecks {

    static final int MIN_SECRET_LENGTH = 32;

    StartupChecks(OtpProperties properties) {
        check(properties);
    }

    static void check(OtpProperties properties) {
        requireSecret("OTP_CODE_PEPPER", properties.codePepper());
        requireSecret("OTP_ADMIN_TOKEN", properties.adminToken());
        if (properties.maxSendsPerIpPerHour() < 1) {
            throw new IllegalStateException("otp.max-sends-per-ip-per-hour must be at least 1.");
        }
        OtpProperties.Sms sms = properties.sms();
        if (sms == null || sms.provider() == null || sms.provider().isBlank()) {
            throw new IllegalStateException("otp.sms.provider is not set.");
        }
        if ("log".equals(sms.provider()) && !sms.allowLog()) {
            throw new IllegalStateException("""
                    otp.sms.provider=log writes every code to the log instead of sending it. That is only for \
                    development: set OTP_SMS_ALLOW_LOG=true to confirm, or configure a real provider.""");
        }
        String whatsapp = properties.whatsapp() == null || properties.whatsapp().provider() == null
                ? "log" : properties.whatsapp().provider();
        boolean twilio = "twilio".equals(sms.provider()) || "twilio".equals(whatsapp);
        if (twilio) {
            requireSet("TWILIO_ACCOUNT_SID", System.getenv("TWILIO_ACCOUNT_SID"));
            requireSet("TWILIO_AUTH_TOKEN", System.getenv("TWILIO_AUTH_TOKEN"));
            if ("twilio".equals(sms.provider())) {
                requireSet("TWILIO_SMS_FROM", System.getenv("TWILIO_SMS_FROM"));
            }
            if ("twilio".equals(whatsapp)) {
                requireSet("TWILIO_WHATSAPP_FROM", System.getenv("TWILIO_WHATSAPP_FROM"));
            }
        }
        for (String provider : new String[] {sms.provider(), whatsapp}) {
            if (!"log".equals(provider) && !"twilio".equals(provider)) {
                throw new IllegalStateException("Unknown provider '" + provider + "'. Use log or twilio.");
            }
        }
        if ("log".equals(whatsapp) && !sms.allowLog()) {
            throw new IllegalStateException("""
                    otp.whatsapp.provider=log writes every code to the log instead of sending it. Set                     OTP_SMS_ALLOW_LOG=true to confirm, or configure a real WhatsApp provider.""");
        }
    }

    private static void requireSet(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set, and a Twilio provider is selected.");
        }
    }

    private static void requireSecret(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set. It has no default on purpose.");
        }
        if (value.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(name + " must be at least " + MIN_SECRET_LENGTH + " characters.");
        }
    }
}
