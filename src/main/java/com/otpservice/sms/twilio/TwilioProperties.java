package com.otpservice.sms.twilio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param accountSid   Twilio Account SID (AC...)
 * @param authToken    Twilio Auth Token
 * @param smsFrom      a Twilio number in E.164, for SMS
 * @param whatsappFrom the WhatsApp sender, e.g. the sandbox's +14155238886
 * @param baseUrl      the API root; changed only by tests
 */
@ConfigurationProperties(prefix = "twilio")
public record TwilioProperties(String accountSid, String authToken, String smsFrom, String whatsappFrom,
                               String baseUrl) {

    public String root() {
        return baseUrl == null || baseUrl.isBlank() ? "https://api.twilio.com" : baseUrl;
    }
}
