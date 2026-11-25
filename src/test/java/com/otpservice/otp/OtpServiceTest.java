package com.otpservice.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.otpservice.app.ClientApp;
import com.otpservice.config.OtpProperties;
import com.otpservice.sms.SmsDeliveryException;
import com.otpservice.sms.SmsMessage;
import com.otpservice.sms.SmsSender;
import com.otpservice.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/** The rules, with the database and the SMS provider replaced and the clock fixed. */
class OtpServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final String PHONE = "+919876543210";
    private static final String PEPPER = "pepper-".repeat(6);
    private static final UUID KEY = UUID.randomUUID();

    private final OtpChallengeRepository challenges = mock(OtpChallengeRepository.class);
    private final SmsSender sms = mock(SmsSender.class);
    private final com.otpservice.sms.WhatsAppSender whatsapp = mock(com.otpservice.sms.WhatsAppSender.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private ClientApp app;
    private OtpService service;

    @BeforeEach
    void setUp() {
        app = new ClientApp();
        app.setId(UUID.randomUUID());
        app.setName("Shop");
        service = serviceAt(NOW);
        when(challenges.lastSendAt(any(), anyString())).thenReturn(Optional.empty());
        when(challenges.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private OtpService serviceAt(Instant instant) {
        return new OtpService(challenges, sms, whatsapp,
                new OtpProperties(PEPPER, "a".repeat(40), 30, new OtpProperties.Sms("log", true),
                        new OtpProperties.WhatsApp("log")),
                jdbc, Clock.fixed(instant, ZoneOffset.UTC));
    }

    // ----------------------------------------------------------------- send

    @Test
    void sendsACodeOfTheAppsLengthAndStoresOnlyItsHash() {
        app.setOtpLength(4);

        SendResult result = service.send(app, KEY, PHONE, "login", "10.0.0.1");

        String code = sentCode();
        assertThat(code).matches("\\d{4}");
        ArgumentCaptor<OtpChallenge> saved = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(challenges).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(result.requestId());
        assertThat(saved.getValue().getCodeHash()).doesNotContain(code).hasSize(64);
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(app.getTtlSeconds()));
    }

    @Test
    void refusesANumberNotInInternationalFormat() {
        assertThatThrownBy(() -> service.send(app, KEY, "9876543210", null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(422));
        verify(sms, never()).send(any());
    }

    @Test
    void refusesTheHourlyLimitForOneNumber() {
        when(challenges.countSends(eq(app.getId()), eq(PHONE), any())).thenReturn((long) app.getMaxSendsPerHour());

        assertThatThrownBy(() -> service.send(app, KEY, PHONE, null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(429));
        verify(sms, never()).send(any());
    }

    @Test
    void refusesAResendInsideTheWaitAndSaysHowLong() {
        when(challenges.lastSendAt(app.getId(), PHONE)).thenReturn(Optional.of(NOW.minusSeconds(10)));

        assertThatThrownBy(() -> service.send(app, KEY, PHONE, null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(429);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(20);
                });
    }

    @Test
    void refusesTooManySendsFromOneAddress() {
        when(challenges.countSendsFromIp(eq("10.0.0.9"), any())).thenReturn(30L);

        assertThatThrownBy(() -> service.send(app, KEY, PHONE, null, "10.0.0.9"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(429));
    }

    @Test
    void aProviderFailureIsA502AndNotAnOrphanedRequestId() {
        org.mockito.Mockito.doThrow(new SmsDeliveryException("down", null)).when(sms).send(any());

        assertThatThrownBy(() -> service.send(app, KEY, PHONE, null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(502));
    }

    @Test
    void anAppOnWhatsAppSendsOnWhatsAppAndRecordsItAndTheKey() {
        app.setChannel(com.otpservice.app.Channel.WHATSAPP);

        SendResult result = service.send(app, KEY, PHONE, null, null);

        assertThat(result.channel()).isEqualTo(com.otpservice.app.Channel.WHATSAPP);
        verify(whatsapp).send(any());
        verify(sms, never()).send(any());
        ArgumentCaptor<OtpChallenge> saved = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(challenges).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getChannel()).isEqualTo(com.otpservice.app.Channel.WHATSAPP);
        assertThat(saved.getValue().getApiKeyId()).isEqualTo(KEY);
    }

    @Test
    void randomPicksBothChannelsOverManySendsAndNeverRecordsRandom() {
        java.util.Set<com.otpservice.app.Channel> seen = java.util.EnumSet.noneOf(com.otpservice.app.Channel.class);
        for (int i = 0; i < 200; i++) {
            seen.add(OtpService.channelFor(com.otpservice.app.Channel.RANDOM));
        }
        assertThat(seen).containsExactlyInAnyOrder(com.otpservice.app.Channel.SMS,
                com.otpservice.app.Channel.WHATSAPP);
    }

    // --------------------------------------------------------------- verify

    @Test
    void theRightCodeVerifiesOnceAndOnlyOnce() {
        OtpChallenge challenge = sendAndCapture();
        String code = sentCode();
        when(challenges.lockForVerify(challenge.getId(), app.getId())).thenReturn(Optional.of(challenge));

        assertThat(service.verify(app, challenge.getId(), code).verified()).isTrue();
        assertThat(service.verify(app, challenge.getId(), code).reason())
                .isEqualTo(VerifyResult.Reason.ALREADY_USED);
    }

    @Test
    void aWrongCodeUsesAnAttemptAndTheLastOneExhaustsThem() {
        app.setMaxAttempts(2);
        OtpChallenge challenge = sendAndCapture();
        String wrong = sentCode().equals("000000") ? "111111" : "000000";
        when(challenges.lockForVerify(challenge.getId(), app.getId())).thenReturn(Optional.of(challenge));

        VerifyResult first = service.verify(app, challenge.getId(), wrong);
        assertThat(first.reason()).isEqualTo(VerifyResult.Reason.INVALID_CODE);
        assertThat(first.attemptsRemaining()).isEqualTo(1);

        assertThat(service.verify(app, challenge.getId(), wrong).reason())
                .isEqualTo(VerifyResult.Reason.ATTEMPTS_EXHAUSTED);
        assertThat(service.verify(app, challenge.getId(), sentCode()).verified())
                .as("even the right code is refused once the attempts are gone")
                .isFalse();
    }

    @Test
    void anExpiredCodeIsRefusedEvenWhenRight() {
        OtpChallenge challenge = sendAndCapture();
        when(challenges.lockForVerify(challenge.getId(), app.getId())).thenReturn(Optional.of(challenge));

        VerifyResult late = serviceAt(NOW.plusSeconds(app.getTtlSeconds())).verify(app, challenge.getId(), sentCode());

        assertThat(late.reason()).isEqualTo(VerifyResult.Reason.EXPIRED);
    }

    @Test
    void anotherAppsRequestIdIsNotFound() {
        UUID someoneElses = UUID.randomUUID();
        when(challenges.lockForVerify(someoneElses, app.getId())).thenReturn(Optional.empty());

        assertThat(service.verify(app, someoneElses, "123456").reason()).isEqualTo(VerifyResult.Reason.NOT_FOUND);
    }

    // ------------------------------------------------------------- fixtures

    private OtpChallenge sendAndCapture() {
        service.send(app, KEY, PHONE, "login", null);
        ArgumentCaptor<OtpChallenge> saved = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(challenges).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    private String sentCode() {
        ArgumentCaptor<SmsMessage> message = ArgumentCaptor.forClass(SmsMessage.class);
        verify(sms, org.mockito.Mockito.atLeastOnce()).send(message.capture());
        Matcher digits = Pattern.compile("\\d{4,8}").matcher(message.getValue().text());
        assertThat(digits.find()).isTrue();
        return digits.group();
    }
}
