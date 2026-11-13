package com.neo4flix.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Reads the logged-in user from the validated JWT: "sub" = user id, "username" = display name. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String id() {
        return jwt().getSubject();
    }

    public static String username() {
        return jwt().getClaimAsString("username");
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** The raw token, used to call another service on behalf of the same user. */
    public static String bearerToken() {
        return jwt().getTokenValue();
    }

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw ApiException.unauthorized("Authentication required");
    }
}
