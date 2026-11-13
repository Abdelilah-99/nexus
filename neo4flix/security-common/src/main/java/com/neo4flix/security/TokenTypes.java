package com.neo4flix.security;

/** Values of the custom "typ" claim, so a half-finished 2FA login can never be used as a real session. */
public final class TokenTypes {

    public static final String CLAIM = "typ";
    /** Normal session token, accepted by every service. */
    public static final String ACCESS = "access";
    /** Issued after a correct password when 2FA is on; only accepted by POST /api/auth/2fa/verify. */
    public static final String MFA_PENDING = "mfa";

    private TokenTypes() {
    }
}
