package com.otpservice.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otpservice.web.ApiException;
import org.junit.jupiter.api.Test;

class AppSettingsTest {

    @Test
    void outOfRangeSettingsAreASentenceNotADatabaseError() {
        ClientApp app = new ClientApp();
        assertThatThrownBy(() -> AppAdminService.apply(app, settings(3, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("otpLength");
    }

    @Test
    void aTemplateWithoutTheCodeIsRefused() {
        ClientApp app = new ClientApp();
        assertThatThrownBy(() -> AppAdminService.apply(app, settings(null, "Hello there")))
                .hasMessageContaining("{code}");
    }

    @Test
    void theMessageFillsInTheCodeAndTheMinutes() {
        ClientApp app = new ClientApp();
        AppAdminService.apply(app, settings(null, "OTP {code}, valid {minutes} min"));
        assertThat(app.messageFor("4821")).isEqualTo("OTP 4821, valid 5 min");
    }

    private static AppAdminService.Settings settings(Integer length, String template) {
        return new AppAdminService.Settings(length, null, null, null, null, null, null, template, null, null);
    }
}
