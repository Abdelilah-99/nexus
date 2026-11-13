package com.neo4flix.user.service;

import java.time.Instant;
import java.util.List;
import com.neo4flix.security.JwtKeys;
import com.neo4flix.security.JwtProperties;
import com.neo4flix.security.TokenTypes;
import com.neo4flix.user.model.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/** The user-service is the only service that <b>issues</b> tokens; the others only verify them. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenService(JwtProperties properties) {
        this.properties = properties;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(JwtKeys.secretKey(properties)));
    }

    public long accessTokenTtl() {
        return properties.accessTokenTtl();
    }

    public String accessToken(User user) {
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();
        return encode(user, TokenTypes.ACCESS, properties.accessTokenTtl(), roles);
    }

    /** Proves "password was correct" for 5 minutes; only accepted by POST /api/auth/2fa/verify. */
    public String mfaPendingToken(User user) {
        return encode(user, TokenTypes.MFA_PENDING, 300, List.of());
    }

    String encode(User user, String type, long ttlSeconds, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .claim("username", user.getUsername())
                .claim("roles", roles)
                .claim(TokenTypes.CLAIM, type)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
