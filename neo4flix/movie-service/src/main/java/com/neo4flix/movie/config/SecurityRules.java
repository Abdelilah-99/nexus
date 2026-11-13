package com.neo4flix.movie.config;

import com.neo4flix.security.AuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityRules {

    /** Anyone can browse the catalogue; only an ADMIN can change it. */
    @Bean
    AuthorizationRules authorizationRules() {
        return auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/movies", "/api/movies/genres", "/api/movies/{id}",
                        "/api/movies/{id}/similar").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/movies/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/movies/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/movies/**").hasRole("ADMIN");
    }
}
