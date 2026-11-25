package com.otpservice.otp;

import com.otpservice.app.Channel;
import com.otpservice.app.ClientApp;
import com.otpservice.config.OtpProperties;
import com.otpservice.sms.SmsDeliveryException;
import com.otpservice.sms.SmsMessage;
import com.otpservice.sms.SmsSender;
import com.otpservice.sms.WhatsAppSender;
import java.security.SecureRandom;
import com.otpservice.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sending a code and checking one, for whichever app's key the request carried.
 *
 * <p><strong>Every read is scoped by app.</strong> A request id from one app is "not found" to another, so a
 * leaked id is no use without the key that made it.
 */
@Service
public class OtpService {

    static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    static final Pattern PURPOSE = Pattern.compile("^[a-z0-9_.-]{1,40}$");

    private final OtpChallengeRepository challenges;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsSender sms;
    private final WhatsAppSender whatsapp;
    private final OtpCodes codes;
    private final OtpProperties properties;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public OtpService(OtpChallengeRepository challenges, SmsSender sms, WhatsAppSender whatsapp,
                      OtpProperties properties, JdbcTemplate jdbc, Clock clock) {
        this.challenges = challenges;
        this.sms = sms;
        this.whatsapp = whatsapp;
        this.codes = new OtpCodes(properties.codePepper());
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Make a code, store its hash, send it.
     *
     * <p>The limits are checked under a transaction-scoped advisory lock on (app, phone), so two requests at the
     * same instant cannot both read "4 of 5 sent" and both send. The message goes out inside the transaction:
     * if the provider refuses, nothing is stored and the caller hears so, rather than holding a request id for
     * a code that never arrived.
     */
    @Transactional
    public SendResult send(ClientApp app, UUID apiKeyId, String phone, String purpose, String requestIp) {
        String to = requirePhone(phone);
        String why = purpose == null || purpose.isBlank() ? "default" : purpose.trim();
        if (!PURPOSE.matcher(why).matches()) {
            throw ApiException.invalid("A purpose is 1 to 40 lower-case letters, digits, '.', '_' or '-'.");
        }

        Instant now = clock.instant();
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtext(?))", Object.class, app.getId() + "|" + to);

        if (requestIp != null && challenges.countSendsFromIp(requestIp, now.minus(Duration.ofHours(1)))
                >= properties.maxSendsPerIpPerHour()) {
            throw ApiException.rateLimited("Too many codes requested from this address. Try again later.", 3600);
        }
        if (challenges.countSends(app.getId(), to, now.minus(Duration.ofHours(1))) >= app.getMaxSendsPerHour()) {
            throw ApiException.rateLimited("Too many codes sent to this number. Try again later.", 3600);
        }
        Instant last = challenges.lastSendAt(app.getId(), to).orElse(null);
        if (last != null) {
            long wait = app.getResendAfterSeconds() - Duration.between(last, now).toSeconds();
            if (wait > 0) {
                throw ApiException.rateLimited("Wait " + wait + " seconds before asking for another code.",
                        (int) wait);
            }
        }

        String code = OtpCodes.generate(app.getOtpLength());
        UUID id = UUID.randomUUID();
        Instant expires = now.plusSeconds(app.getTtlSeconds());
        Channel channel = channelFor(app.getChannel());
        challenges.saveAndFlush(new OtpChallenge(id, app.getId(), to, why, codes.hash(id, code), now, expires,
                app.getMaxAttempts(), requestIp, channel, apiKeyId));

        SmsMessage message = new SmsMessage(to, app.messageFor(code), app.getSenderId(), app.getTemplateId());
        try {
            if (channel == Channel.WHATSAPP) {
                whatsapp.send(message);
            } else {
                sms.send(message);
            }
        } catch (SmsDeliveryException failure) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SMS_FAILED",
                    "The message could not be sent. Try again in a moment.");
        }
        return new SendResult(id, expires, app.getResendAfterSeconds(), channel);
    }

    /**
     * Check a code. A wrong guess uses an attempt and is committed even though the answer is "no", so the limit
     * holds; a right one marks the challenge used, so the same code cannot be spent twice.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public VerifyResult verify(ClientApp app, UUID requestId, String code) {
        if (requestId == null || code == null || !code.matches("\\d{4,8}")) {
            throw ApiException.invalid("Give the requestId from the send and the code, 4 to 8 digits.");
        }
        OtpChallenge challenge = challenges.lockForVerify(requestId, app.getId()).orElse(null);
        if (challenge == null) {
            return VerifyResult.refused(VerifyResult.Reason.NOT_FOUND, 0);
        }
        Instant now = clock.instant();
        if (challenge.isVerified()) {
            return VerifyResult.refused(VerifyResult.Reason.ALREADY_USED, 0);
        }
        if (challenge.isExpired(now)) {
            return VerifyResult.refused(VerifyResult.Reason.EXPIRED, 0);
        }
        if (challenge.attemptsExhausted()) {
            return VerifyResult.refused(VerifyResult.Reason.ATTEMPTS_EXHAUSTED, 0);
        }

        challenge.recordAttempt();
        if (codes.matches(challenge.getId(), code, challenge.getCodeHash())) {
            challenge.markVerified(now);
            challenges.save(challenge);
            return VerifyResult.ok();
        }
        challenges.save(challenge);
        return challenge.attemptsExhausted()
                ? VerifyResult.refused(VerifyResult.Reason.ATTEMPTS_EXHAUSTED, 0)
                : VerifyResult.refused(VerifyResult.Reason.INVALID_CODE, challenge.attemptsRemaining());
    }

    /** RANDOM picks one of the two for this send; the challenge records which. */
    static Channel channelFor(Channel configured) {
        if (configured == Channel.RANDOM) {
            return RANDOM.nextBoolean() ? Channel.SMS : Channel.WHATSAPP;
        }
        return configured == null ? Channel.SMS : configured;
    }

    static String requirePhone(String phone) {
        String trimmed = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        if (!E164.matcher(trimmed).matches()) {
            throw ApiException.invalid("Give the phone number in international format, such as +919876543210.");
        }
        return trimmed;
    }
}
