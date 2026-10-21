package com.smart.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.smart.Dao.UserRepository;
import com.smart.entity.User;

class UserDetailsServiceImplTest {

    @Test
    void testLoadUserByUsername_UserFound() {

        UserRepository userRepository = mock(UserRepository.class);

        User user = new User();
        user.setName("Test User");
        user.setEmail("testuser@gmail.com");
        user.setPassword("password123");
        user.setRole("USER");
        user.setEnabled(true);

        when(userRepository.getUserByName("testuser@gmail.com"))
                .thenReturn(user);

        UserDetailsServiceImpl service = new UserDetailsServiceImpl();

        // private field inject करण्यासाठी reflection
        org.springframework.test.util.ReflectionTestUtils.setField(
                service,
                "userRepository",
                userRepository
        );

        UserDetails result =
                service.loadUserByUsername("testuser@gmail.com");

        assertNotNull(result);
        assertEquals("testuser@gmail.com", result.getUsername());
        assertEquals("password123", result.getPassword());
        assertTrue(result instanceof CustomUserDetails);

        verify(userRepository).getUserByName("testuser@gmail.com");
    }

    @Test
    void testLoadUserByUsername_UserNotFound() {

        UserRepository userRepository = mock(UserRepository.class);

        when(userRepository.getUserByName("unknown@gmail.com"))
                .thenReturn(null);

        UserDetailsServiceImpl service = new UserDetailsServiceImpl();

        org.springframework.test.util.ReflectionTestUtils.setField(
                service,
                "userRepository",
                userRepository
        );

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("unknown@gmail.com")
        );

        verify(userRepository).getUserByName("unknown@gmail.com");
    }
}