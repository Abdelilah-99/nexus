package com.neo4flix.user.web;

import java.util.List;
import jakarta.validation.constraints.NotBlank;

public final class TwoFactorDtos {

    private TwoFactorDtos() {
    }

    /** secret: to type into the app by hand; otpauthUri: the content of the QR code. */
    public record SetupResponse(String secret, String otpauthUri) {
    }

    /** Recovery codes are shown once, at activation, and only their hashes are kept. */
    public record EnableResponse(List<String> recoveryCodes) {
    }

    public record CodeRequest(@NotBlank String code) {
    }

    public record VerifyRequest(@NotBlank String mfaToken, @NotBlank String code) {
    }

    public record DisableRequest(@NotBlank String password, @NotBlank String code) {
    }
}
