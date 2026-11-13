package com.neo4flix.user.config;

import com.neo4flix.security.AuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityRules {

    @Bean
    AuthorizationRules authorizationRules() {
        return auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN");
    }

    /** BCrypt with cost 12: slow on purpose, so a stolen hash is expensive to brute-force. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
