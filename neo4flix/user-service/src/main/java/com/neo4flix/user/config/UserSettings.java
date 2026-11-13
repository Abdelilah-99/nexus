package com.neo4flix.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "neo4flix")
public record UserSettings(Admin admin, Login login) {

    public record Admin(String username, String email, String password) {
    }

    /** Brute-force protection: after maxFailures wrong passwords the account is locked for lockMinutes. */
    public record Login(int maxFailures, int lockMinutes) {
    }
}
