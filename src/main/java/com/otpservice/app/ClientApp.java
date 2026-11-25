package com.otpservice.app;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An application that sends OTPs through this service, with its own settings. */
@Entity
@Table(name = "client_app")
public class ClientApp {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "otp_length", nullable = false)
    private short otpLength = 6;

    @Column(name = "ttl_seconds", nullable = false)
    private int ttlSeconds = 300;

    @Column(name = "max_attempts", nullable = false)
    private short maxAttempts = 5;

    @Column(name = "max_sends_per_hour", nullable = false)
    private short maxSendsPerHour = 5;

    @Column(name = "resend_after_seconds", nullable = false)
    private int resendAfterSeconds = 30;

    @Column(name = "sender_id", length = 20)
    private String senderId;

    @Column(name = "template_id", length = 64)
    private String templateId;

    @Column(name = "message_template", nullable = false, length = 300)
    private String messageTemplate =
            "Your verification code is {code}. It expires in {minutes} minutes. Do not share it.";

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel = Channel.SMS;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** The message for one code, with {@code {code}} and {@code {minutes}} filled in. */
    public String messageFor(String code) {
        return messageTemplate.replace("{code}", code)
                .replace("{minutes}", String.valueOf(Math.max(1, ttlSeconds / 60)));
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getOtpLength() { return otpLength; }
    public void setOtpLength(int otpLength) { this.otpLength = (short) otpLength; }
    public int getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = (short) maxAttempts; }
    public int getMaxSendsPerHour() { return maxSendsPerHour; }
    public void setMaxSendsPerHour(int maxSendsPerHour) { this.maxSendsPerHour = (short) maxSendsPerHour; }
    public int getResendAfterSeconds() { return resendAfterSeconds; }
    public void setResendAfterSeconds(int resendAfterSeconds) { this.resendAfterSeconds = resendAfterSeconds; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getMessageTemplate() { return messageTemplate; }
    public void setMessageTemplate(String messageTemplate) { this.messageTemplate = messageTemplate; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
