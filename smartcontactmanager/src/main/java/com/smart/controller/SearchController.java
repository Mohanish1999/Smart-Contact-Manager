package com.smart.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.smart.Dao.ContactRepository;
import com.smart.Dao.UserRepository;
import com.smart.dto.ContactSummary;
import com.smart.entity.Contact;
import com.smart.entity.User;

@RestController
public class SearchController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContactRepository contactRepository;

    /**
     * Search-box suggestions. Only the logged-in user's own contacts are searched, and only a safe
     * summary is returned (previously the whole Contact entity, including its owner's password hash).
     */
    @GetMapping("/search/{query}")
    public ResponseEntity<List<ContactSummary>> search(@PathVariable("query") String query, Principal principal) {
        User user = userRepository.getUserByName(principal.getName());
        List<Contact> contacts = contactRepository.findByNameContainingAndUser(query, user);
        return ResponseEntity.ok(contacts.stream().map(ContactSummary::from).toList());
    }
}
