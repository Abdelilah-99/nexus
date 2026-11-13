package com.neo4flix.rating.config;

import com.neo4flix.security.AuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityRules {

    /** Reading the ratings of a movie is public (shown on the details page); everything else needs a login. */
    @Bean
    AuthorizationRules authorizationRules() {
        return auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/ratings/movie/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN");
    }
}
