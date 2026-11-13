package com.neo4flix.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {

    /** RFC 6238 appendix B test vectors (SHA-1, 8 digits). */
    @Test
    void matchesTheRfcTestVectors() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.code(key, 59 / 30, 8)).isEqualTo("94287082");
        assertThat(Totp.code(key, 1111111109L / 30, 8)).isEqualTo("07081804");
        assertThat(Totp.code(key, 1234567890L / 30, 8)).isEqualTo("89005924");
        assertThat(Totp.code(key, 20000000000L / 30, 8)).isEqualTo("65353130");
    }

    @Test
    void base32RoundTrips() {
        byte[] data = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.base32Encode(data)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.base32Decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")).isEqualTo(data);
    }

    @Test
    void verifiesCurrentCodeAndToleratesOneStepOfDrift() {
        String secret = Totp.newSecret();
        Instant now = Instant.now();
        long step = Totp.step(now);
        assertThat(Totp.verify(secret, Totp.code(secret, step), now)).hasValue(step);
        assertThat(Totp.verify(secret, Totp.code(secret, step - 1), now)).hasValue(step - 1);
        assertThat(Totp.verify(secret, Totp.code(secret, step - 3), now)).isEmpty();
        assertThat(Totp.verify(secret, "abc123", now)).isEmpty();
    }
}
