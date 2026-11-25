package com.otpservice.otp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One code sent. The code is never stored, only its HMAC (see {@link OtpCodes}). */
@Entity
@Table(name = "otp_challenge")
public class OtpChallenge {

    @Id
    private UUID id;

    @Column(name = "app_id", nullable = false, updatable = false)
    private UUID appId;

    @Column(nullable = false, length = 20, updatable = false)
    private String phone;

    @Column(nullable = false, length = 40, updatable = false)
    private String purpose;

    @Column(name = "code_hash", nullable = false, length = 64, updatable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private short attempts;

    @Column(name = "max_attempts", nullable = false, updatable = false)
    private short maxAttempts;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** The channel it actually went out on: never RANDOM. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private com.otpservice.app.Channel channel;

    /** The key that asked for it. */
    @Column(name = "api_key_id", updatable = false)
    private UUID apiKeyId;

    @Column(name = "request_ip", length = 64, updatable = false)
    private String requestIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OtpChallenge() {
    }

    OtpChallenge(UUID id, UUID appId, String phone, String purpose, String codeHash, Instant createdAt,
                 Instant expiresAt, int maxAttempts, String requestIp, com.otpservice.app.Channel channel,
                 UUID apiKeyId) {
        this.channel = channel;
        this.apiKeyId = apiKeyId;
        this.id = id;
        this.appId = appId;
        this.phone = phone;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.maxAttempts = (short) maxAttempts;
        this.requestIp = requestIp;
    }

    boolean isVerified() { return verifiedAt != null; }
    boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
    boolean attemptsExhausted() { return attempts >= maxAttempts; }
    int attemptsRemaining() { return Math.max(0, maxAttempts - attempts); }
    void recordAttempt() { attempts++; }
    void markVerified(Instant now) { verifiedAt = now; }

    public UUID getId() { return id; }
    public UUID getAppId() { return appId; }
    public String getPhone() { return phone; }
    public String getPurpose() { return purpose; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public com.otpservice.app.Channel getChannel() { return channel; }
    public UUID getApiKeyId() { return apiKeyId; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public String getRequestIp() { return requestIp; }

    /** What happened to it, as of {@code now}. Derived, never stored. */
    public String status(Instant now) {
        if (isVerified()) {
            return "VERIFIED";
        }
        if (attemptsExhausted()) {
            return "ATTEMPTS_EXHAUSTED";
        }
        return isExpired(now) ? "EXPIRED" : "PENDING";
    }
}
