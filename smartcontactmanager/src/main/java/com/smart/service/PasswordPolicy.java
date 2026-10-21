package com.smart.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The application's password rules, used by registration, change password and password reset.
 * Minimum length is configurable with {@code app.password.min-length} (default 8).
 * The 72-byte ceiling is BCrypt's own limit: anything longer would be silently ignored.
 */
@Component
public class PasswordPolicy {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final int minLength;

    public PasswordPolicy(@Value("${app.password.min-length:8}") int minLength) {
        this.minLength = Math.max(minLength, 6);
    }

    public int getMinLength() {
        return minLength;
    }

    /** One-line description shown next to password fields. */
    public String describe() {
        return "Use at least " + minLength + " characters, including a letter and a number.";
    }

    /** The rules the password breaks; empty when it is acceptable. */
    public List<String> violations(String password) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < minLength) {
            problems.add("at least " + minLength + " characters");
        }
        if (password == null || password.chars().noneMatch(Character::isLetter)) {
            problems.add("a letter");
        }
        if (password == null || password.chars().noneMatch(Character::isDigit)) {
            problems.add("a number");
        }
        return problems;
    }

    /** A user-facing error message, or empty when the password is acceptable. */
    public Optional<String> check(String password) {
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return Optional.of("Password is too long (maximum " + BCRYPT_MAX_BYTES + " bytes).");
        }
        List<String> problems = violations(password);
        if (problems.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Password must contain " + String.join(", ", problems) + ".");
    }
}
