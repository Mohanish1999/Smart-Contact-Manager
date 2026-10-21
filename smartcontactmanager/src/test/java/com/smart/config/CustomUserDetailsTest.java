package com.smart.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.smart.entity.User;

class CustomUserDetailsTest {

    @Test
    void testCustomUserDetails() {

        User user = new User();

        user.setName("Test User");
        user.setEmail("customuser@gmail.com");
        user.setPassword("password123");
        user.setRole("USER");
        user.setEnabled(true);

        CustomUserDetails userDetails = new CustomUserDetails(user);

        // getUsername()
        assertEquals("customuser@gmail.com", userDetails.getUsername());

        // getPassword()
        assertEquals("password123", userDetails.getPassword());

        // getAuthorities()
        assertNotNull(userDetails.getAuthorities());
        assertEquals(1, userDetails.getAuthorities().size());
        assertEquals("USER",
                userDetails.getAuthorities().iterator().next().getAuthority());

        // account status methods
        assertTrue(userDetails.isAccountNonExpired());
        assertTrue(userDetails.isAccountNonLocked());
        assertTrue(userDetails.isCredentialsNonExpired());

        // isEnabled()
        assertTrue(userDetails.isEnabled());
    }
}