package com.otpservice.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development only: writes the message, code included, to the log. {@code StartupChecks} refuses to start with
 * this unless {@code otp.sms.allow-log=true} says so deliberately.
 */
@Component
@ConditionalOnProperty(name = "otp.sms.provider", havingValue = "log")
class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(SmsMessage message) {
        log.warn("SMS NOT SENT (log provider) to={} sender={} template={} text=\"{}\"",
                message.to(), message.senderId(), message.templateId(), message.text());
    }
}
