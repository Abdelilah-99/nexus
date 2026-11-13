package com.neo4flix.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher("a-test-key-that-is-at-least-32-characters");

    @Test
    void encryptsWithARandomIvAndDecryptsBack() {
        String a = cipher.encrypt("JBSWY3DPEHPK3PXP");
        String b = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(a).isNotEqualTo(b).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(a)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void detectsTampering() {
        String stored = cipher.encrypt("secret");
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherKeyCannotDecrypt() {
        String stored = cipher.encrypt("secret");
        SecretCipher other = new SecretCipher("another-key-that-is-at-least-32-characters");
        assertThatThrownBy(() -> other.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }
}
