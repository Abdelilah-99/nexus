package com.neo4flix.user.service;

import java.util.ArrayList;
import java.util.List;
import com.neo4flix.security.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Strong password rules, checked on registration and on every password change:
 * 10 to 128 characters, at least one lower-case letter, one upper-case letter, one digit and one symbol,
 * no whitespace, and it must not contain the username.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    public List<String> violations(String password, String username) {
        List<String> problems = new ArrayList<>();
        if (password == null) {
            problems.add("Password is required");
            return problems;
        }
        if (password.length() < MIN_LENGTH) {
            problems.add("Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("Password must be at most " + MAX_LENGTH + " characters long");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            problems.add("Password must contain a lower-case letter");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            problems.add("Password must contain an upper-case letter");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            problems.add("Password must contain a digit");
        }
        if (password.chars().allMatch(Character::isLetterOrDigit)) {
            problems.add("Password must contain a symbol");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            problems.add("Password must not contain spaces");
        }
        if (username != null && !username.isBlank() && password.toLowerCase().contains(username.toLowerCase())) {
            problems.add("Password must not contain the username");
        }
        return problems;
    }

    public void validate(String password, String username) {
        List<String> problems = violations(password, username);
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
        }
    }
}
