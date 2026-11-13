package com.neo4flix.security;

import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Each service declares one bean of this type with its own URL rules
 * (which endpoints are public, which need ROLE_ADMIN, ...).
 * Whatever is not matched by the rules requires an authenticated user.
 */
@FunctionalInterface
public interface AuthorizationRules {

    void configure(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry registry);
}
