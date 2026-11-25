package com.otpservice.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development only, like {@link LoggingSmsSender}; refused at startup unless allowed deliberately. */
@Component
@ConditionalOnProperty(name = "otp.whatsapp.provider", havingValue = "log", matchIfMissing = true)
class LoggingWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingWhatsAppSender.class);

    @Override
    public void send(SmsMessage message) {
        log.warn("WHATSAPP NOT SENT (log provider) to={} template={} text=\"{}\"",
                message.to(), message.templateId(), message.text());
    }
}
