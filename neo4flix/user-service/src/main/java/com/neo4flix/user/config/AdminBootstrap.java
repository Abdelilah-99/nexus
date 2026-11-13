package com.neo4flix.user.config;

import java.util.Set;
import java.util.UUID;
import com.neo4flix.user.model.Role;
import com.neo4flix.user.model.User;
import com.neo4flix.user.repository.UserRepository;
import com.neo4flix.user.service.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates the first ADMIN account from ADMIN_USERNAME / ADMIN_PASSWORD, only if it does not exist yet. */
@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    @Order(1)
    ApplicationRunner createAdmin(UserSettings settings, UserRepository users, PasswordEncoder encoder,
            PasswordPolicy policy) {
        return args -> {
            UserSettings.Admin admin = settings.admin();
            if (admin.password() == null || admin.password().isBlank()) {
                log.info("ADMIN_PASSWORD not set: no admin account created");
                return;
            }
            String username = admin.username().toLowerCase();
            if (users.existsByUsername(username)) {
                return;
            }
            policy.validate(admin.password(), username);
            users.save(new User(UUID.randomUUID().toString(), username, admin.email().toLowerCase(),
                    encoder.encode(admin.password()), Set.of(Role.USER, Role.ADMIN)));
            log.info("Admin account '{}' created", username);
        };
    }
}
