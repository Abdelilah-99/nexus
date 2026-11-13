package com.neo4flix.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neo4flix.security.ApiException;
import com.neo4flix.user.config.UserSettings;
import org.junit.jupiter.api.Test;

class LoginAttemptsTest {

    private final LoginAttempts attempts = new LoginAttempts(
            new UserSettings(null, new UserSettings.Login(3, 15)));

    @Test
    void locksAfterTooManyFailures() {
        attempts.recordFailure("alice");
        attempts.recordFailure("alice");
        assertThatCode(() -> attempts.checkNotLocked("alice")).doesNotThrowAnyException();
        attempts.recordFailure("alice");
        assertThatThrownBy(() -> attempts.checkNotLocked("alice")).isInstanceOf(ApiException.class);
    }

    @Test
    void successResetsTheCounter() {
        attempts.recordFailure("bob");
        attempts.recordFailure("bob");
        attempts.recordSuccess("bob");
        attempts.recordFailure("bob");
        assertThatCode(() -> attempts.checkNotLocked("bob")).doesNotThrowAnyException();
    }
}
