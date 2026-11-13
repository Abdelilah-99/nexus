package com.neo4flix.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtKeysTest {

    private final JwtProperties properties =
            new JwtProperties("test-secret-test-secret-test-secret-123", "neo4flix", 3600);

    private String token(String type, String issuer, Instant expiresAt) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(JwtKeys.secretKey(properties)));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).subject("u1").issuedAt(expiresAt.minusSeconds(60)).expiresAt(expiresAt)
                .claim(TokenTypes.CLAIM, type).claim("roles", List.of("USER"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void acceptsAValidAccessToken() {
        String token = token(TokenTypes.ACCESS, "neo4flix", Instant.now().plusSeconds(300));
        assertThat(JwtKeys.decoder(properties, TokenTypes.ACCESS).decode(token).getSubject()).isEqualTo("u1");
    }

    @Test
    void rejectsAPendingTwoFactorTokenAsAccessToken() {
        String token = token(TokenTypes.MFA_PENDING, "neo4flix", Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> JwtKeys.decoder(properties, TokenTypes.ACCESS).decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        String token = token(TokenTypes.ACCESS, "neo4flix", Instant.now().minusSeconds(600));
        assertThatThrownBy(() -> JwtKeys.decoder(properties, TokenTypes.ACCESS).decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsAnotherIssuer() {
        String token = token(TokenTypes.ACCESS, "someone-else", Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> JwtKeys.decoder(properties, TokenTypes.ACCESS).decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void refusesAShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "neo4flix", 60))
                .isInstanceOf(IllegalStateException.class);
    }
}
