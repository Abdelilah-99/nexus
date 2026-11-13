package com.neo4flix.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings shared by every service.
 *
 * @param secret          HMAC-SHA256 key; must be at least 32 bytes
 * @param issuer          value of the "iss" claim; tokens from another issuer are rejected
 * @param accessTokenTtl  lifetime of an access token in seconds
 */
@ConfigurationProperties(prefix = "neo4flix.jwt")
public record JwtProperties(String secret, String issuer, long accessTokenTtl) {

    public JwtProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("neo4flix.jwt.secret (JWT_SECRET) must be at least 32 bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "neo4flix";
        }
        if (accessTokenTtl <= 0) {
            accessTokenTtl = 3600;
        }
    }
}
