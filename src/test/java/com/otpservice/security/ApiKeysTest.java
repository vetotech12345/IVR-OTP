package com.otpservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeysTest {

    @Test
    void aGeneratedKeyParsesAndMatchesItsOwnHash() {
        ApiKeys.NewKey key = ApiKeys.generate();

        ApiKeys.Presented presented = ApiKeys.parse(key.plain()).orElseThrow();

        assertThat(presented.prefix()).isEqualTo(key.prefix());
        assertThat(ApiKeys.matches(presented, key.secretHash())).isTrue();
        assertThat(key.plain()).doesNotContain(key.secretHash());
    }

    @Test
    void theRightPrefixWithTheWrongSecretDoesNotMatch() {
        ApiKeys.NewKey key = ApiKeys.generate();
        ApiKeys.Presented forged = new ApiKeys.Presented(key.prefix(), "x".repeat(43));

        assertThat(ApiKeys.matches(forged, key.secretHash())).isFalse();
    }

    @Test
    void malformedKeysAreRefusedBeforeAnyLookup() {
        assertThat(ApiKeys.parse(null)).isEmpty();
        assertThat(ApiKeys.parse("")).isEmpty();
        assertThat(ApiKeys.parse("sk_0123456789ab_" + "a".repeat(43))).isEmpty();
        assertThat(ApiKeys.parse("otp_NOTHEX_" + "a".repeat(43))).isEmpty();
        assertThat(ApiKeys.parse("otp_0123456789ab_short")).isEmpty();
    }

    @Test
    void twoKeysAreNeverTheSame() {
        assertThat(ApiKeys.generate().plain()).isNotEqualTo(ApiKeys.generate().plain());
    }
}
