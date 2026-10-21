package com.smart.Dao;

import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.smart.entity.Contact;
import com.smart.entity.User;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ContactRepositoryTest {

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testFindContactsByUser() {

        User user = new User();
        user.setName("Test User");
        user.setEmail("contacttest@gmail.com");
        user.setPassword("password123");
        user.setRole("USER");
        user.setEnabled(true);

        userRepository.save(user);

        Contact contact = new Contact();
        contact.setName("Rahul");
        contact.setEmail("rahul@gmail.com");
        contact.setPhone("9876543210");
        contact.setSecondname("Patil");
        contact.setWork("Developer");
        contact.setUser(user);

        contactRepository.save(contact);

        Page<Contact> result =
                contactRepository.findContactsByUser(
                        user.getId(),
                        PageRequest.of(0, 10)
                );

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("Rahul", result.getContent().get(0).getName());
    }
    @Test
    void testFindByNameContainingAndUser() {

        User user = new User();
        user.setName("Test User");
        user.setEmail("contacttest2@gmail.com");
        user.setPassword("password123");
        user.setRole("USER");
        user.setEnabled(true);

        userRepository.save(user);

        Contact contact = new Contact();
        contact.setName("Rahul");
        contact.setEmail("rahul2@gmail.com");
        contact.setPhone("9876543210");
        contact.setSecondname("Patil");
        contact.setWork("Developer");
        contact.setUser(user);

        contactRepository.save(contact);

        List<Contact> result =
                contactRepository.findByNameContainingAndUser("Rah", user);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("Rahul", result.get(0).getName());
    }
}