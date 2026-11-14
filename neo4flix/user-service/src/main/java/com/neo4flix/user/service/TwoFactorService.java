package com.neo4flix.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalLong;
import com.neo4flix.security.ApiException;
import com.neo4flix.security.JwtKeys;
import com.neo4flix.security.JwtProperties;
import com.neo4flix.security.TokenTypes;
import com.neo4flix.user.model.User;
import com.neo4flix.user.repository.UserRepository;
import com.neo4flix.user.web.AuthDtos.LoginResponse;
import com.neo4flix.user.web.TwoFactorDtos.EnableResponse;
import com.neo4flix.user.web.TwoFactorDtos.SetupResponse;
import com.neo4flix.user.web.UserDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Two-factor authentication with TOTP.
 * setup → the user scans the QR code → enable (proves the app works) → every login now needs a code.
 */
@Service
public class TwoFactorService {

    private static final String ISSUER = "Neo4flix";
    private static final int RECOVERY_CODES = 8;
    private static final String RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private final UserRepository users;
    private final SecretCipher cipher;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final LoginAttempts attempts;
    private final JwtDecoder mfaDecoder;
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(UserRepository users, SecretCipher cipher, PasswordEncoder encoder, TokenService tokens,
            LoginAttempts attempts, JwtProperties jwt) {
        this.users = users;
        this.cipher = cipher;
        this.encoder = encoder;
        this.tokens = tokens;
        this.attempts = attempts;
        this.mfaDecoder = JwtKeys.decoder(jwt, TokenTypes.MFA_PENDING);
    }

    public SetupResponse setup(String userId) {
        User user = load(userId);
        if (user.isTotpEnabled()) {
            throw ApiException.conflict("Two-factor authentication is already enabled");
        }
        String secret = Totp.newSecret();
        user.setPendingTotpSecret(cipher.encrypt(secret));
        users.save(user);
        return new SetupResponse(secret, Totp.otpauthUri(ISSUER, user.getUsername(), secret));
    }

    public EnableResponse enable(String userId, String code) {
        User user = load(userId);
        if (user.getPendingTotpSecret() == null) {
            throw ApiException.badRequest("Call /api/auth/2fa/setup first");
        }
        String secret = cipher.decrypt(user.getPendingTotpSecret());
        OptionalLong step = Totp.verify(secret, code, Instant.now());
        if (step.isEmpty()) {
            throw ApiException.badRequest("Invalid code: check the time on your phone and try again");
        }
        List<String> recoveryCodes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String recovery = randomRecoveryCode();
            recoveryCodes.add(recovery);
            hashes.add(sha256(recovery));
        }
        user.setTotpSecret(user.getPendingTotpSecret());
        user.setPendingTotpSecret(null);
        user.setTotpEnabled(true);
        user.setLastTotpStep(step.getAsLong());
        user.setRecoveryCodeHashes(hashes);
        users.save(user);
        return new EnableResponse(recoveryCodes);
    }

    public void disable(String userId, String password, String code) {
        User user = load(userId);
        if (!user.isTotpEnabled()) {
            throw ApiException.badRequest("Two-factor authentication is not enabled");
        }
        if (!encoder.matches(password, user.getPasswordHash()) || !checkCode(user, code)) {
            throw ApiException.badRequest("Wrong password or code");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setRecoveryCodeHashes(List.of());
        users.save(user);
    }

    public LoginResponse verifyLogin(String mfaToken, String code) {
        String userId;
        try {
            Jwt jwt = mfaDecoder.decode(mfaToken);
            userId = jwt.getSubject();
        } catch (JwtException e) {
            throw ApiException.unauthorized("Login session expired, please log in again");
        }
        String key = "2fa:" + userId;
        attempts.checkNotLocked(key);
        User user = load(userId);
        if (!user.isTotpEnabled() || !checkCode(user, code)) {
            attempts.recordFailure(key);
            throw ApiException.unauthorized("Invalid code");
        }
        attempts.recordSuccess(key);
        return LoginResponse.session(tokens.accessToken(user), tokens.accessTokenTtl(), UserDto.of(user));
    }

    /** Accepts a fresh TOTP code, or consumes one recovery code. Saves the user when something changed. */
    private boolean checkCode(User user, String code) {
        String trimmed = code.trim().replace(" ", "");
        OptionalLong step = Totp.verify(cipher.decrypt(user.getTotpSecret()), trimmed, Instant.now());
        if (step.isPresent()) {
            if (step.getAsLong() <= user.getLastTotpStep()) {
                return false; // replay: this code (or an older one) was already used
            }
            user.setLastTotpStep(step.getAsLong());
            users.save(user);
            return true;
        }
        byte[] candidate = sha256(trimmed.toLowerCase()).getBytes(StandardCharsets.US_ASCII);
        List<String> hashes = new ArrayList<>(user.getRecoveryCodeHashes());
        for (Iterator<String> it = hashes.iterator(); it.hasNext(); ) {
            if (MessageDigest.isEqual(candidate, it.next().getBytes(StandardCharsets.US_ASCII))) {
                it.remove();
                user.setRecoveryCodeHashes(hashes);
                users.save(user);
                return true;
            }
        }
        return false;
    }

    /**
     * Recovery codes are random (10 characters from 31, about 50 bits), so a fast SHA-256 is enough to make a stolen
     * hash useless. BCrypt is meant for low-entropy human passwords: here it made each 2FA check cost ~2 s of CPU
     * (8 hashes × 250 ms), which also let anyone burn server CPU with wrong codes.
     */
    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String randomRecoveryCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                code.append('-');
            }
            code.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
        }
        return code.toString();
    }

    private User load(String userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
