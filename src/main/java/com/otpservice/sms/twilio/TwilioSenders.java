package com.otpservice.sms.twilio;

import com.otpservice.sms.SmsSender;
import com.otpservice.sms.WhatsAppSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@code OTP_SMS_PROVIDER=twilio} and/or {@code OTP_WHATSAPP_PROVIDER=twilio}. */
@Configuration
@EnableConfigurationProperties(TwilioProperties.class)
@ConditionalOnExpression("'${otp.sms.provider:}' == 'twilio' or '${otp.whatsapp.provider:}' == 'twilio'")
class TwilioSenders {

    @Bean
    TwilioClient twilioClient(TwilioProperties properties) {
        return new TwilioClient(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "otp.sms.provider", havingValue = "twilio")
    SmsSender twilioSms(TwilioClient client, TwilioProperties properties) {
        return message -> client.send(properties.smsFrom(), message.to(), message.text());
    }

    @Bean
    @ConditionalOnProperty(name = "otp.whatsapp.provider", havingValue = "twilio")
    WhatsAppSender twilioWhatsApp(TwilioClient client, TwilioProperties properties) {
        return message -> client.send("whatsapp:" + properties.whatsappFrom(), "whatsapp:" + message.to(),
                message.text());
    }
}
