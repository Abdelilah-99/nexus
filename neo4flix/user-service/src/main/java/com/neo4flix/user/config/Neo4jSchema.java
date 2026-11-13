package com.neo4flix.user.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.neo4j.core.Neo4jClient;

/** Uniqueness constraints on (:User). They also create the indexes used by the lookups. */
@Configuration
public class Neo4jSchema {

    @Bean
    @Order(0)
    ApplicationRunner userConstraints(Neo4jClient neo4j) {
        return args -> {
            neo4j.query("CREATE CONSTRAINT user_id IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE").run();
            neo4j.query("CREATE CONSTRAINT user_username IF NOT EXISTS FOR (u:User) REQUIRE u.username IS UNIQUE").run();
            neo4j.query("CREATE CONSTRAINT user_email IF NOT EXISTS FOR (u:User) REQUIRE u.email IS UNIQUE").run();
        };
    }
}
