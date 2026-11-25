package com.otpservice.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OtpCodesTest {

    private final OtpCodes codes = new OtpCodes("p".repeat(40));

    @Test
    void codesHaveTheAskedLengthIncludingLeadingZeros() {
        for (int i = 0; i < 500; i++) {
            assertThat(OtpCodes.generate(6)).matches("\\d{6}");
            assertThat(OtpCodes.generate(4)).matches("\\d{4}");
        }
        assertThatThrownBy(() -> OtpCodes.generate(3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtpCodes.generate(9)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRightCodeMatchesAndAWrongOneDoesNot() {
        UUID id = UUID.randomUUID();
        String hash = codes.hash(id, "123456");

        assertThat(codes.matches(id, "123456", hash)).isTrue();
        assertThat(codes.matches(id, "123457", hash)).isFalse();
    }

    @Test
    void theSameCodeHashesDifferentlyOnAnotherChallenge() {
        assertThat(codes.hash(UUID.randomUUID(), "123456")).isNotEqualTo(codes.hash(UUID.randomUUID(), "123456"));
    }

    @Test
    void aDifferentPepperGivesADifferentHash() {
        UUID id = UUID.randomUUID();
        assertThat(codes.hash(id, "123456")).isNotEqualTo(new OtpCodes("q".repeat(40)).hash(id, "123456"));
    }
}
