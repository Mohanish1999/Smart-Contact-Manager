package com.smart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(8);

    @Test
    void acceptsAStrongEnoughPassword() {
        assertTrue(policy.check("Passw0rdX").isEmpty());
    }

    @Test
    void rejectsShortPasswordsAndPasswordsWithoutLetterOrDigit() {
        assertTrue(policy.check("ab1").isPresent());
        assertTrue(policy.check("abcdefghi").isPresent());
        assertTrue(policy.check("12345678").isPresent());
        assertTrue(policy.check("").isPresent());
        assertTrue(policy.check(null).isPresent());
    }

    @Test
    void rejectsPasswordsLongerThanBcryptCanUse() {
        assertTrue(policy.check("a1".repeat(40)).isPresent());
    }

    @Test
    void messageNamesWhatIsMissing() {
        assertTrue(policy.check("abcdefgh").get().contains("a number"));
        assertTrue(policy.check("12345678").get().contains("a letter"));
    }

    @Test
    void minimumLengthIsConfigurableButNeverBelowSix() {
        assertEquals(12, new PasswordPolicy(12).getMinLength());
        assertEquals(6, new PasswordPolicy(2).getMinLength());
        assertFalse(new PasswordPolicy(12).check("Passw0rdX").isEmpty());
    }
}
