package com.neo4flix.user.web;

import java.time.Instant;
import java.util.List;
import com.neo4flix.user.model.User;

/** What the API exposes about a user: never the password hash. */
public record UserDto(String id, String username, String email, String displayName, String bio,
        List<String> roles, Instant createdAt) {

    public static UserDto of(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), user.getBio(),
                user.getRoles().stream().map(Enum::name).sorted().toList(), user.getCreatedAt());
    }

    /** Public view shown to other users (no e-mail). */
    public record PublicUser(String id, String username, String displayName, String bio) {

        public static PublicUser of(User user) {
            return new PublicUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getBio());
        }
    }
}
