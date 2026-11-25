package com.otpservice.sms;

/**
 * Delivers a message. The only place a provider's name or API may appear is an implementation of this.
 *
 * <p>Throw {@link SmsDeliveryException} when the provider refuses or cannot be reached: the send is then
 * rolled back, and the caller is told rather than left waiting for a code that never comes.
 */
public interface SmsSender {

    void send(SmsMessage message);
}
