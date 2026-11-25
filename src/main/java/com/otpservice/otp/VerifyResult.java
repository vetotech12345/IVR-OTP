package com.otpservice.otp;

/**
 * The answer to a verify. {@code reason} is null when verified.
 *
 * <p>Reasons are specific because the caller is an application, not a stranger: it holds the API key and the
 * request id, and "expired, send a new one" is a different screen from "wrong code, try again".
 */
public record VerifyResult(boolean verified, Reason reason, int attemptsRemaining) {

    public enum Reason { INVALID_CODE, EXPIRED, ATTEMPTS_EXHAUSTED, ALREADY_USED, NOT_FOUND }

    static VerifyResult ok() {
        return new VerifyResult(true, null, 0);
    }

    static VerifyResult refused(Reason reason, int attemptsRemaining) {
        return new VerifyResult(false, reason, attemptsRemaining);
    }
}
