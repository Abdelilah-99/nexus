package com.neo4flix.user.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * A (:User) node. Only the user-service writes these properties.
 * Relationships to other nodes (RATED, FRIEND, IN_WATCHLIST) are written with Cypher,
 * so saving this entity never touches them.
 */
@Node("User")
public class User {

    @Id
    private String id;
    /** Always stored in lower case, unique (see {@code Neo4jSchema}). */
    private String username;
    private String email;
    /** BCrypt hash; the plain password is never stored. */
    private String passwordHash;
    private String displayName;
    private String bio;
    private Set<Role> roles = new HashSet<>();
    private Instant createdAt;

    protected User() {
    }

    public User(String id, String username, String email, String passwordHash, Set<Role> roles) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = username;
        this.roles = new HashSet<>(roles);
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public Set<Role> getRoles() { return roles; }
    public Instant getCreatedAt() { return createdAt; }
}
