package com.otpservice.otp;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Making a code and checking one.
 *
 * <p><strong>Why HMAC with a pepper, bound to the challenge id.</strong> A 6-digit code has a million values,
 * so a plain hash in a leaked database is reversed in milliseconds. Keying it with a secret that lives outside
 * the database makes the table alone useless; binding the challenge id means the same code on two challenges
 * hashes differently.
 */
public final class OtpCodes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final byte[] pepper;

    public OtpCodes(String pepper) {
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    /** Uniform over every value of the given length, leading zeros included. */
    public static String generate(int length) {
        if (length < 4 || length > 8) {
            throw new IllegalArgumentException("A code is 4 to 8 digits.");
        }
        int bound = (int) Math.pow(10, length);
        return String.format("%0" + length + "d", RANDOM.nextInt(bound));
    }

    public String hash(UUID challengeId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HEX.formatHex(mac.doFinal((challengeId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Constant-time comparison. */
    public boolean matches(UUID challengeId, String code, String storedHash) {
        return MessageDigest.isEqual(hash(challengeId, code).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
