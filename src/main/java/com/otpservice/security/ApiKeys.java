package com.otpservice.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * API key format: {@code otp_<prefix>_<secret>}.
 *
 * <ul>
 *   <li>{@code prefix}: 12 hex characters, public. It is how a presented key is found, and it is safe to show
 *       in a list or a log.</li>
 *   <li>{@code secret}: 32 random bytes, URL-safe base64. Stored only as its SHA-256. A fast hash is right
 *       here: the secret has 256 bits of entropy, so there is nothing to brute-force.</li>
 * </ul>
 */
public final class ApiKeys {

    static final String SCHEME = "otp";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private ApiKeys() {
    }

    /** A new key. {@code plain} is shown to the caller once and never stored. */
    public record NewKey(String prefix, String secretHash, String plain) {
    }

    /** A presented key, split. Not yet checked against anything. */
    public record Presented(String prefix, String secret) {
    }

    public static NewKey generate() {
        byte[] prefixBytes = new byte[6];
        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(prefixBytes);
        RANDOM.nextBytes(secretBytes);
        String prefix = HEX.formatHex(prefixBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        return new NewKey(prefix, sha256(secret), SCHEME + "_" + prefix + "_" + secret);
    }

    public static Optional<Presented> parse(String presented) {
        if (presented == null) {
            return Optional.empty();
        }
        String[] parts = presented.trim().split("_", 3);
        if (parts.length != 3 || !SCHEME.equals(parts[0])
                || !parts[1].matches("[0-9a-f]{12}") || parts[2].length() < 20) {
            return Optional.empty();
        }
        return Optional.of(new Presented(parts[1], parts[2]));
    }

    /** Constant-time: how long this takes says nothing about how much of the secret was right. */
    public static boolean matches(Presented presented, String storedHash) {
        return MessageDigest.isEqual(
                sha256(presented.secret()).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    static String sha256(String value) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
