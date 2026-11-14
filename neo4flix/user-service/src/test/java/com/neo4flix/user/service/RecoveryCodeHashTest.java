package com.neo4flix.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecoveryCodeHashTest {

    @Test
    void hashesAreStableHexAndNeverTheCodeItself() {
        String hash = TwoFactorService.sha256("jpsxt-u58ej");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+").isEqualTo(TwoFactorService.sha256("jpsxt-u58ej"));
        assertThat(hash).doesNotContain("jpsxt");
        assertThat(TwoFactorService.sha256("jpsxt-u58ek")).isNotEqualTo(hash);
    }
}
