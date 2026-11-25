package com.otpservice.sms;

/**
 * Delivers a message on WhatsApp. Like {@link SmsSender}, a provider's name or API appears only in an
 * implementation. WhatsApp OTPs need an approved authentication template on the business account.
 */
public interface WhatsAppSender {

    void send(SmsMessage message);
}
