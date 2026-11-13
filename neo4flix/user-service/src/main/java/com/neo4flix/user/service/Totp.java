package com.neo4flix.user.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238), the algorithm used by Google Authenticator, Authy, ...
 * code = HOTP(secret, floor(unixTime / 30)), HOTP = HMAC-SHA1 truncated to 6 digits (RFC 4226).
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    /** Accept the previous and next 30 s window too, to tolerate clock drift between phone and server. */
    public static final int ALLOWED_DRIFT_STEPS = 1;

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** 160-bit random secret, Base32 encoded (the format authenticator apps expect). */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static String otpauthUri(String issuer, String account, String secret) {
        return "otpauth://totp/" + issuer + ":" + account + "?secret=" + secret + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    public static long step(Instant time) {
        return time.getEpochSecond() / PERIOD_SECONDS;
    }

    public static String code(String base32Secret, long step) {
        return code(base32Decode(base32Secret), step, DIGITS);
    }

    static String code(byte[] key, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    /**
     * Returns the time step the code matched, or empty if it is wrong.
     * The caller stores the step and refuses any code whose step is not newer (replay protection).
     */
    public static OptionalLong verify(String base32Secret, String code, Instant now) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = step(now);
        for (long s = current - ALLOWED_DRIFT_STEPS; s <= current + ALLOWED_DRIFT_STEPS; s++) {
            if (java.security.MessageDigest.isEqual(code(base32Secret, s).getBytes(), code.getBytes())) {
                return OptionalLong.of(s);
            }
        }
        return OptionalLong.empty();
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] base32Decode(String text) {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }
}
