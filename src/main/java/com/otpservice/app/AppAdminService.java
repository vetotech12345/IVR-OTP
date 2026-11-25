package com.otpservice.app;

import com.otpservice.security.ApiKeys;
import com.otpservice.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating and configuring apps, and issuing and revoking their keys. Behind the admin token. */
@Service
public class AppAdminService {

    /** Settings an app may change. Null means "leave as it is". */
    public record Settings(Integer otpLength, Integer ttlSeconds, Integer maxAttempts, Integer maxSendsPerHour,
                           Integer resendAfterSeconds, String senderId, String templateId, String messageTemplate,
                           Channel channel, Boolean active) {
    }

    public record AppView(UUID id, String name, int otpLength, int ttlSeconds, int maxAttempts, int maxSendsPerHour,
                          int resendAfterSeconds, String senderId, String templateId, String messageTemplate,
                          Channel channel, boolean active, Instant createdAt) {
    }

    public record KeyView(UUID id, String prefix, String label, Instant createdAt, Instant lastUsedAt,
                          Instant revokedAt) {
    }

    /** The only response that ever carries a key in full. */
    public record IssuedKey(KeyView key, String apiKey) {
    }

    public record CreatedApp(AppView app, IssuedKey firstKey) {
    }

    private final ClientAppRepository apps;
    private final ApiKeyRepository keys;

    AppAdminService(ClientAppRepository apps, ApiKeyRepository keys) {
        this.apps = apps;
        this.keys = keys;
    }

    @Transactional
    public CreatedApp create(String name, Settings settings) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw ApiException.invalid("An app needs a name of 1 to 100 characters.");
        }
        if (apps.existsByNameIgnoreCase(trimmed)) {
            throw ApiException.conflict("An app called " + trimmed + " already exists.");
        }
        ClientApp app = new ClientApp();
        app.setName(trimmed);
        apply(app, settings);
        ClientApp saved = apps.saveAndFlush(app);
        return new CreatedApp(view(saved), issue(saved.getId(), "first key"));
    }

    @Transactional(readOnly = true)
    public List<AppView> list() {
        return apps.findAllByOrderByNameAsc().stream().map(AppAdminService::view).toList();
    }

    @Transactional(readOnly = true)
    public AppView get(UUID appId) {
        return view(require(appId));
    }

    @Transactional
    public AppView update(UUID appId, Settings settings) {
        ClientApp app = require(appId);
        apply(app, settings);
        return view(apps.saveAndFlush(app));
    }

    @Transactional
    public IssuedKey newKey(UUID appId, String label) {
        require(appId);
        return issue(appId, label);
    }

    @Transactional(readOnly = true)
    public List<KeyView> keys(UUID appId) {
        require(appId);
        return keys.findAllByAppIdOrderByCreatedAtDesc(appId).stream().map(AppAdminService::view).toList();
    }

    /** Takes effect on the next request. Revoking twice is harmless and keeps the first time. */
    @Transactional
    public KeyView revoke(UUID appId, UUID keyId) {
        ApiKey key = keys.findByIdAndAppId(keyId, appId).orElseThrow(() -> ApiException.notFound("That key"));
        if (!key.isRevoked()) {
            key.setRevokedAt(Instant.now());
        }
        return view(keys.saveAndFlush(key));
    }

    private IssuedKey issue(UUID appId, String label) {
        ApiKeys.NewKey generated = ApiKeys.generate();
        ApiKey key = new ApiKey();
        key.setAppId(appId);
        key.setPrefix(generated.prefix());
        key.setSecretHash(generated.secretHash());
        key.setLabel(label == null || label.isBlank() ? null : label.trim());
        return new IssuedKey(view(keys.saveAndFlush(key)), generated.plain());
    }

    private ClientApp require(UUID appId) {
        return apps.findById(appId).orElseThrow(() -> ApiException.notFound("That app"));
    }

    /** The same bounds as the schema's CHECKs, so a bad value is a sentence rather than a 500. */
    static void apply(ClientApp app, Settings settings) {
        if (settings == null) {
            return;
        }
        if (settings.otpLength() != null) {
            app.setOtpLength(bounded("otpLength", settings.otpLength(), 4, 8));
        }
        if (settings.ttlSeconds() != null) {
            app.setTtlSeconds(bounded("ttlSeconds", settings.ttlSeconds(), 60, 1800));
        }
        if (settings.maxAttempts() != null) {
            app.setMaxAttempts(bounded("maxAttempts", settings.maxAttempts(), 1, 10));
        }
        if (settings.maxSendsPerHour() != null) {
            app.setMaxSendsPerHour(bounded("maxSendsPerHour", settings.maxSendsPerHour(), 1, 100));
        }
        if (settings.resendAfterSeconds() != null) {
            app.setResendAfterSeconds(bounded("resendAfterSeconds", settings.resendAfterSeconds(), 0, 600));
        }
        if (settings.senderId() != null) {
            app.setSenderId(settings.senderId().isBlank() ? null : limit("senderId", settings.senderId(), 20));
        }
        if (settings.templateId() != null) {
            app.setTemplateId(settings.templateId().isBlank() ? null : limit("templateId", settings.templateId(), 64));
        }
        if (settings.messageTemplate() != null) {
            String template = limit("messageTemplate", settings.messageTemplate(), 300);
            if (!template.contains("{code}")) {
                throw ApiException.invalid("messageTemplate must contain {code}.");
            }
            app.setMessageTemplate(template);
        }
        if (settings.channel() != null) {
            app.setChannel(settings.channel());
        }
        if (settings.active() != null) {
            app.setActive(settings.active());
        }
    }

    private static int bounded(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw ApiException.invalid(field + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private static String limit(String field, String value, int max) {
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw ApiException.invalid(field + " can be at most " + max + " characters.");
        }
        return trimmed;
    }

    static AppView view(ClientApp app) {
        return new AppView(app.getId(), app.getName(), app.getOtpLength(), app.getTtlSeconds(),
                app.getMaxAttempts(), app.getMaxSendsPerHour(), app.getResendAfterSeconds(), app.getSenderId(),
                app.getTemplateId(), app.getMessageTemplate(), app.getChannel(), app.isActive(), app.getCreatedAt());
    }

    static KeyView view(ApiKey key) {
        return new KeyView(key.getId(), key.getPrefix(), key.getLabel(), key.getCreatedAt(), key.getLastUsedAt(),
                key.getRevokedAt());
    }
}
