package com.otpservice.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StartupChecksTest {

    private static final String GOOD = "s".repeat(40);

    @Test
    void aMissingOrShortSecretStopsStartup() {
        assertThatThrownBy(() -> StartupChecks.check(props(null, GOOD, "log", true)))
                .hasMessageContaining("OTP_CODE_PEPPER");
        assertThatThrownBy(() -> StartupChecks.check(props(GOOD, "short", "log", true)))
                .hasMessageContaining("OTP_ADMIN_TOKEN");
    }

    @Test
    void theLogProviderNeedsToBeChosenDeliberately() {
        assertThatThrownBy(() -> StartupChecks.check(props(GOOD, GOOD, "log", false)))
                .hasMessageContaining("OTP_SMS_ALLOW_LOG");
        assertThatCode(() -> StartupChecks.check(props(GOOD, GOOD, "log", true))).doesNotThrowAnyException();
    }

    private static OtpProperties props(String pepper, String admin, String provider, boolean allowLog) {
        return new OtpProperties(pepper, admin, 30, new OtpProperties.Sms(provider, allowLog),
                new OtpProperties.WhatsApp("log"));
    }
}
