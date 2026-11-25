package com.otpservice.otp;

import java.time.Instant;
import java.util.UUID;

/** What the caller needs to verify later. Never the code. */
public record SendResult(UUID requestId, Instant expiresAt, int resendAfterSeconds,
                         com.otpservice.app.Channel channel) {
}
