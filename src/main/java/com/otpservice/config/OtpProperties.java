package com.otpservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Service-wide settings. Per-app settings (length, expiry, attempts, limits) live on the app.
 *
 * @param codePepper            server-side secret mixed into every code hash
 * @param adminToken            bearer token for {@code /admin/**}
 * @param maxSendsPerIpPerHour  sends from one IP across all apps, per hour
 * @param sms                   which provider sends SMS
 * @param whatsapp              which provider sends WhatsApp
 */
@ConfigurationProperties(prefix = "otp")
public record OtpProperties(String codePepper, String adminToken, int maxSendsPerIpPerHour, Sms sms,
                            WhatsApp whatsapp) {

    /** Same rule as SMS: {@code log} only with {@code otp.sms.allow-log=true}. */
    public record WhatsApp(String provider) {
    }

    public record Sms(String provider, boolean allowLog) {
    }
}
