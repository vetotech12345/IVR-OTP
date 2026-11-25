package com.otpservice.app;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One API key of one app. Only the SHA-256 of the secret half is kept; the full key is shown once, when made.
 * An app may hold several, so a key can be replaced before the old one is revoked.
 */
@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    private UUID id;

    @Column(name = "app_id", nullable = false, updatable = false)
    private UUID appId;

    @Column(nullable = false, length = 16, updatable = false)
    private String prefix;

    @Column(name = "secret_hash", nullable = false, length = 64, updatable = false)
    private String secretHash;

    @Column(length = 100)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public UUID getId() { return id; }
    public UUID getAppId() { return appId; }
    public void setAppId(UUID appId) { this.appId = appId; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public String getSecretHash() { return secretHash; }
    public void setSecretHash(String secretHash) { this.secretHash = secretHash; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
