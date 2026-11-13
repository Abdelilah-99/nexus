package com.neo4flix.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsAStrongPassword() {
        assertThat(policy.violations("Graph!Movies42", "alice")).isEmpty();
    }

    @Test
    void rejectsShortPasswords() {
        assertThat(policy.violations("Ab1!", "alice")).anyMatch(v -> v.contains("at least"));
    }

    @Test
    void requiresEveryCharacterClass() {
        assertThat(policy.violations("alllowercase", "bob"))
                .anyMatch(v -> v.contains("upper-case"))
                .anyMatch(v -> v.contains("digit"))
                .anyMatch(v -> v.contains("symbol"));
    }

    @Test
    void rejectsPasswordsContainingTheUsername() {
        assertThat(policy.violations("Alice!2024xyz", "alice")).anyMatch(v -> v.contains("username"));
    }

    @Test
    void rejectsSpaces() {
        assertThat(policy.violations("Graph Movies 42!", "bob")).anyMatch(v -> v.contains("spaces"));
    }
}
