package com.otpservice.web;

import com.otpservice.app.ApiKey;
import com.otpservice.app.ApiKeyRepository;
import com.otpservice.app.Channel;
import com.otpservice.app.ClientApp;
import com.otpservice.app.ClientAppRepository;
import com.otpservice.otp.OtpChallenge;
import com.otpservice.otp.OtpChallengeRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the operator's console reads: usage across apps, and the log of codes sent.
 *
 * <p><strong>No code ever appears here</strong>: the service never stores one, only its HMAC, so there is
 * nothing to show. The log says who, where, which key, which channel, when, and what became of it.
 */
@Service
class ConsoleService {

    record AppUsage(UUID appId, String name, Channel channel, boolean active, long activeKeys, long sends24h,
                    long verified24h, long sends7d, long verified7d, Instant lastSendAt) {
    }

    record Overview(long apps, long activeApps, long activeKeys, long sends24h, long verified24h, long sends7d,
                    long verified7d, Map<Channel, Long> channels7d, List<AppUsage> perApp) {
    }

    record LogRow(UUID requestId, UUID appId, String appName, String phone, String purpose, Channel channel,
                  String status, int attempts, int maxAttempts, Instant createdAt, Instant expiresAt,
                  Instant verifiedAt, String keyPrefix, String keyLabel, String requestIp) {
    }

    record LogPage(List<LogRow> rows, int page, int size, long totalElements, int totalPages) {
    }

    private final ClientAppRepository apps;
    private final ApiKeyRepository keys;
    private final OtpChallengeRepository challenges;
    private final Clock clock;

    ConsoleService(ClientAppRepository apps, ApiKeyRepository keys, OtpChallengeRepository challenges, Clock clock) {
        this.apps = apps;
        this.keys = keys;
        this.challenges = challenges;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        Instant now = clock.instant();
        Instant day = now.minus(Duration.ofDays(1));
        Instant week = now.minus(Duration.ofDays(7));

        List<ClientApp> all = apps.findAllByOrderByNameAsc();
        Map<UUID, Long> activeKeys = keys.findAll().stream().filter(key -> !key.isRevoked())
                .collect(Collectors.groupingBy(ApiKey::getAppId, Collectors.counting()));
        Map<UUID, Object[]> byDay = byApp(challenges.usageByApp(day));
        Map<UUID, Object[]> byWeek = byApp(challenges.usageByApp(week));
        Map<UUID, Object[]> last = byApp(challenges.lastSendByApp());

        List<AppUsage> perApp = all.stream().map(app -> new AppUsage(app.getId(), app.getName(), app.getChannel(),
                app.isActive(), activeKeys.getOrDefault(app.getId(), 0L),
                number(byDay.get(app.getId()), 1), number(byDay.get(app.getId()), 2),
                number(byWeek.get(app.getId()), 1), number(byWeek.get(app.getId()), 2),
                last.containsKey(app.getId()) ? (Instant) last.get(app.getId())[1] : null)).toList();

        Map<Channel, Long> channels = new EnumMap<>(Channel.class);
        channels.put(Channel.SMS, 0L);
        channels.put(Channel.WHATSAPP, 0L);
        for (Object[] row : challenges.usageByChannel(week)) {
            channels.put((Channel) row[0], ((Number) row[1]).longValue());
        }

        return new Overview(all.size(), all.stream().filter(ClientApp::isActive).count(),
                activeKeys.values().stream().mapToLong(Long::longValue).sum(),
                challenges.countSince(day), challenges.countVerifiedSince(day),
                challenges.countSince(week), challenges.countVerifiedSince(week), channels, perApp);
    }

    /** Newest first. {@code phone} matches anywhere in the number, so "98765" finds +919876543210. */
    @Transactional(readOnly = true)
    public LogPage log(UUID appId, String phone, String status, int page, int size) {
        Instant now = clock.instant();
        Specification<OtpChallenge> filter = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (appId != null) {
                where.add(cb.equal(root.get("appId"), appId));
            }
            if (phone != null && !phone.isBlank()) {
                where.add(cb.like(root.get("phone"), "%" + phone.replaceAll("[^0-9+]", "") + "%"));
            }
            // The same rules as OtpChallenge.status(now), in SQL, so a page is a page of matching rows.
            if (status != null && !status.isBlank()) {
                var verified = cb.isNotNull(root.get("verifiedAt"));
                var exhausted = cb.greaterThanOrEqualTo(root.<Integer>get("attempts"), root.<Integer>get("maxAttempts"));
                var expired = cb.lessThanOrEqualTo(root.<Instant>get("expiresAt"), now);
                switch (status) {
                    case "VERIFIED" -> where.add(verified);
                    case "ATTEMPTS_EXHAUSTED" -> where.add(cb.and(cb.not(verified), exhausted));
                    case "EXPIRED" -> where.add(cb.and(cb.not(verified), cb.not(exhausted), expired));
                    case "PENDING" -> where.add(cb.and(cb.not(verified), cb.not(exhausted), cb.not(expired)));
                    default -> throw ApiException.invalid(
                            "status is one of VERIFIED, PENDING, EXPIRED, ATTEMPTS_EXHAUSTED.");
                }
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<OtpChallenge> found = challenges.findAll(filter,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200), Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<UUID, ClientApp> appsById = apps.findAllById(found.getContent().stream()
                .map(OtpChallenge::getAppId).distinct().toList()).stream()
                .collect(Collectors.toMap(ClientApp::getId, Function.identity()));
        Map<UUID, ApiKey> keysById = new HashMap<>();
        keys.findAllById(found.getContent().stream().map(OtpChallenge::getApiKeyId)
                .filter(java.util.Objects::nonNull).distinct().toList())
                .forEach(key -> keysById.put(key.getId(), key));

        List<LogRow> rows = found.getContent().stream()
                .map(c -> {
                    ClientApp app = appsById.get(c.getAppId());
                    ApiKey key = c.getApiKeyId() == null ? null : keysById.get(c.getApiKeyId());
                    return new LogRow(c.getId(), c.getAppId(), app == null ? null : app.getName(), c.getPhone(),
                            c.getPurpose(), c.getChannel(), c.status(now), c.getAttempts(), c.getMaxAttempts(),
                            c.getCreatedAt(), c.getExpiresAt(), c.getVerifiedAt(),
                            key == null ? null : key.getPrefix(), key == null ? null : key.getLabel(),
                            c.getRequestIp());
                })
                .toList();
        return new LogPage(rows, found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    private static Map<UUID, Object[]> byApp(List<Object[]> rows) {
        Map<UUID, Object[]> map = new HashMap<>();
        rows.forEach(row -> map.put((UUID) row[0], row));
        return map;
    }

    private static long number(Object[] row, int index) {
        return row == null || row[index] == null ? 0L : ((Number) row[index]).longValue();
    }
}
