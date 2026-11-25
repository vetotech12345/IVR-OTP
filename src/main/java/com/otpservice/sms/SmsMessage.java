package com.otpservice.sms;

/**
 * One message to send. {@code senderId} and {@code templateId} are the app's DLT header and template in India;
 * a provider that does not need them ignores them.
 */
public record SmsMessage(String to, String text, String senderId, String templateId) {
}
