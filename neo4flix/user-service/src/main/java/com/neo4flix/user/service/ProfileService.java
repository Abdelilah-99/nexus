package com.neo4flix.user.service;

import com.neo4flix.security.ApiException;
import com.neo4flix.user.model.User;
import com.neo4flix.user.repository.UserRepository;
import com.neo4flix.user.web.ProfileDtos.ChangePasswordRequest;
import com.neo4flix.user.web.ProfileDtos.UpdateProfileRequest;
import com.neo4flix.user.web.UserDto;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final Neo4jClient neo4j;

    public ProfileService(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy, Neo4jClient neo4j) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.neo4j = neo4j;
    }

    public User get(String userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    public UserDto update(String userId, UpdateProfileRequest request) {
        User user = get(userId);
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.bio() != null) {
            user.setBio(request.bio().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equals(user.getEmail()) && users.existsByEmail(email)) {
                throw ApiException.conflict("E-mail is already registered");
            }
            user.setEmail(email);
        }
        return UserDto.of(users.save(user));
    }

    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = get(userId);
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is wrong");
        }
        policy.validate(request.newPassword(), user.getUsername());
        user.setPasswordHash(encoder.encode(request.newPassword()));
        users.save(user);
    }

    /** Deletes the user node and every relationship attached to it (ratings, watchlist, friends). */
    public void delete(String userId, String password) {
        User user = get(userId);
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("Password is wrong");
        }
        neo4j.query("MATCH (u:User {id: $id}) DETACH DELETE u").bind(userId).to("id").run();
    }

    public void deleteByAdmin(String userId) {
        get(userId);
        neo4j.query("MATCH (u:User {id: $id}) DETACH DELETE u").bind(userId).to("id").run();
    }
}
