package com.neo4flix.user.service;

import java.util.Set;
import java.util.UUID;
import com.neo4flix.security.ApiException;
import com.neo4flix.user.model.Role;
import com.neo4flix.user.model.User;
import com.neo4flix.user.repository.UserRepository;
import com.neo4flix.user.web.AuthDtos.LoginRequest;
import com.neo4flix.user.web.AuthDtos.LoginResponse;
import com.neo4flix.user.web.AuthDtos.RegisterRequest;
import com.neo4flix.user.web.UserDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Invalid username or password";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final TokenService tokens;
    private final LoginAttempts attempts;

    public AuthService(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy, TokenService tokens,
            LoginAttempts attempts) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.tokens = tokens;
        this.attempts = attempts;
    }

    public UserDto register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase();
        String email = request.email().trim().toLowerCase();
        policy.validate(request.password(), username);
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("Username is already taken");
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("E-mail is already registered");
        }
        User user = new User(UUID.randomUUID().toString(), username, email, encoder.encode(request.password()),
                Set.of(Role.USER));
        return UserDto.of(users.save(user));
    }

    public LoginResponse login(LoginRequest request) {
        String login = request.login().trim().toLowerCase();
        attempts.checkNotLocked(login);
        User user = (login.contains("@") ? users.findByEmail(login) : users.findByUsername(login)).orElse(null);
        // Same message whether the user exists or not, so the API cannot be used to discover accounts.
        if (user == null || !encoder.matches(request.password(), user.getPasswordHash())) {
            attempts.recordFailure(login);
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        attempts.recordSuccess(login);
        return LoginResponse.session(tokens.accessToken(user), tokens.accessTokenTtl(), UserDto.of(user));
    }
}
