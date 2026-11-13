package com.neo4flix.user.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record UpdateProfileRequest(@Size(max = 50) String displayName, @Size(max = 500) String bio,
            @Email @Size(max = 254) String email) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record DeleteAccountRequest(@NotBlank String password) {
    }
}
