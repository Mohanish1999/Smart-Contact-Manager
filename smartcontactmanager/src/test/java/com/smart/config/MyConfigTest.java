package com.smart.config;

import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
@ActiveProfiles("test")
@SpringBootTest
class MyConfigTest {

    @Test
    void testUserDetailsService() {

        MyConfig config = new MyConfig();

        UserDetailsService service = config.userDetailsService();

        assertNotNull(service);
        assertTrue(service instanceof UserDetailsServiceImpl);
    }

    @Test
    void testPasswordEncoder() {

        MyConfig config = new MyConfig();

        BCryptPasswordEncoder encoder = config.passwordEncoder();

        assertNotNull(encoder);

        String password = "password123";
        String encodedPassword = encoder.encode(password);

        assertNotEquals(password, encodedPassword);
        assertTrue(encoder.matches(password, encodedPassword));
    }

    @Test
    void testAuthenticationProvider() {

        MyConfig config = new MyConfig();

        DaoAuthenticationProvider provider =
                config.authenticationProvider();

        assertNotNull(provider);
    }
    
}