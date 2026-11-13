package com.neo4flix.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Turns every service into an OAuth2 <b>resource server</b>: each request must carry
 * "Authorization: Bearer &lt;JWT&gt;" signed with the shared key. No HTTP session, no cookies.
 */
@AutoConfiguration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
@Import(GlobalExceptionHandler.class)
public class SecurityAutoConfiguration {

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        return JwtKeys.decoder(properties, TokenTypes.ACCESS);
    }

    /** Maps the "roles" claim (["USER","ADMIN"]) to Spring authorities ROLE_USER / ROLE_ADMIN. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthorizationRules rules,
            JwtAuthenticationConverter converter) throws Exception {
        http
                // Tokens travel in a header, not in a cookie, so CSRF cannot happen: disabling it is safe here.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**", "/actuator/health", "/error").permitAll();
                    rules.configure(auth);
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, e) ->
                                ApiError.write(response, HttpStatus.UNAUTHORIZED, "Missing or invalid token")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                ApiError.write(response, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, e) ->
                                ApiError.write(response, HttpStatus.FORBIDDEN, "Request is not permitted")));
        return http.build();
    }
}
