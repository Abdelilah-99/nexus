package com.neo4flix.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.neo4flix.security.ApiException;
import com.neo4flix.user.config.UserSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Counts failed logins per username and locks the account for a while after too many failures.
 * Kept in memory: enough for one instance; a shared store (Redis) would be needed with several replicas.
 */
@Component
public class LoginAttempts {

    private record Failures(int count, Instant lockedUntil) {
    }

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockDuration;

    public LoginAttempts(UserSettings settings) {
        this.maxFailures = settings.login().maxFailures();
        this.lockDuration = Duration.ofMinutes(settings.login().lockMinutes());
    }

    public void checkNotLocked(String key) {
        Failures current = failures.get(key);
        if (current != null && current.lockedUntil() != null && current.lockedUntil().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Try again in a few minutes.");
        }
    }

    public void recordFailure(String key) {
        failures.compute(key, (k, current) -> {
            int count = current == null || current.lockedUntil() != null ? 1 : current.count() + 1;
            return new Failures(count, count >= maxFailures ? Instant.now().plus(lockDuration) : null);
        });
    }

    public void recordSuccess(String key) {
        failures.remove(key);
    }
}
