package com.neo4flix.recommendation.config;

import com.neo4flix.security.AuthorizationRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.boot.ApplicationRunner;

@Configuration
public class SecurityRules {

    /** Recommendations are personal: every endpoint needs a logged-in user (the default rule). */
    @Bean
    AuthorizationRules authorizationRules() {
        return auth -> { };
    }

    @Bean
    ApplicationRunner shareSchema(Neo4jClient neo4j) {
        return args -> neo4j.query("CREATE CONSTRAINT share_id IF NOT EXISTS FOR (s:Share) REQUIRE s.id IS UNIQUE")
                .run();
    }
}
