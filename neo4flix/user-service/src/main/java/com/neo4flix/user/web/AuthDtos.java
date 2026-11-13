package com.neo4flix.user.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 30)
            @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "only letters, digits, '.', '_' and '-'")
            String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String password) {
    }

    /** "login" accepts either the username or the e-mail address. */
    public record LoginRequest(@NotBlank String login, @NotBlank String password) {
    }

    /**
     * Either a full session (accessToken set) or, when 2FA is enabled,
     * mfaRequired=true and a short-lived mfaToken to send with the TOTP code.
     */
    public record LoginResponse(String accessToken, String tokenType, Long expiresIn, UserDto user,
            boolean mfaRequired, String mfaToken) {

        public static LoginResponse session(String token, long expiresIn, UserDto user) {
            return new LoginResponse(token, "Bearer", expiresIn, user, false, null);
        }

        public static LoginResponse mfa(String mfaToken) {
            return new LoginResponse(null, null, null, null, true, mfaToken);
        }
    }
}
