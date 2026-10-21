package com.smart.Dao;

import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

import com.smart.entity.User;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void testFindByEmail() {

        User user = new User();

        
        user.setName("Test User");
        user.setEmail("testuser123@gmail.com");
        user.setPassword("password123");
        user.setRole("USER");
        user.setEnabled(true);
        user.setImageurl("default.png");
        user.setAbout("Test user for JUnit testing");
        user.setPassword("Test@123456");

        userRepository.save(user);

        User result = userRepository.findByEmail("test@gmail.com");

        assertNotNull(result);
        assertEquals("test@gmail.com", result.getEmail());
    }
}