package com.smart.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.test.web.servlet.MockMvc;

import com.smart.Dao.ContactRepository;
import com.smart.Dao.UserRepository;
import com.smart.entity.Contact;
import com.smart.entity.User;


@WebMvcTest(SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchControllerTest {


    @Autowired
    private MockMvc mockMvc;


    @MockBean
    private UserRepository userRepository;


    @MockBean
    private ContactRepository contactRepository;


    // =====================================================
    // 1. SEARCH - CONTACTS FOUND
    // =====================================================

    @Test
    void searchContactsFoundTest()
            throws Exception {

        User user = new User();

        Contact contact1 = new Contact();
        contact1.setCid(1);
        contact1.setName("Rahul");

        Contact contact2 = new Contact();
        contact2.setCid(2);
        contact2.setName("Rahul Sharma");

        List<Contact> contacts =
                Arrays.asList(
                        contact1,
                        contact2
                );


        when(userRepository.getUserByName(
                "test@gmail.com"
        )).thenReturn(user);


        when(
                contactRepository
                        .findByNameContainingAndUser(
                                "Rahul",
                                user
                        )
        ).thenReturn(contacts);


        mockMvc.perform(
                get("/search/Rahul")
                        .principal(
                                () -> "test@gmail.com"
                        )
        )
        .andExpect(
                status().isOk()
        )
        .andExpect(
                content().contentType(
                        "application/json"
                )
        )
        .andExpect(
                jsonPath("$.length()")
                        .value(2)
        )
        .andExpect(
                jsonPath("$[0].name")
                        .value("Rahul")
        )
        .andExpect(
                jsonPath("$[1].name")
                        .value("Rahul Sharma")
        );
    }


    // =====================================================
    // 2. SEARCH - NO CONTACT FOUND
    // =====================================================

    @Test
    void searchNoContactsTest()
            throws Exception {

        User user = new User();

        when(userRepository.getUserByName(
                "test@gmail.com"
        )).thenReturn(user);


        when(
                contactRepository
                        .findByNameContainingAndUser(
                                "XYZ",
                                user
                        )
        ).thenReturn(
                Collections.emptyList()
        );


        mockMvc.perform(
                get("/search/XYZ")
                        .principal(
                                () -> "test@gmail.com"
                        )
        )
        .andExpect(
                status().isOk()
        )
        .andExpect(
                content().contentType(
                        "application/json"
                )
        )
        .andExpect(
                jsonPath("$.length()")
                        .value(0)
        );
    }


    // =====================================================
    // 3. SEARCH - QUERY PASSED CORRECTLY
    // =====================================================

    @Test
    void searchQueryTest()
            throws Exception {

        User user = new User();

        Contact contact = new Contact();

        contact.setCid(10);
        contact.setName("Amit");

        when(userRepository.getUserByName(
                "test@gmail.com"
        )).thenReturn(user);


        when(
                contactRepository
                        .findByNameContainingAndUser(
                                "Amit",
                                user
                        )
        ).thenReturn(
                Collections.singletonList(
                        contact
                )
        );


        mockMvc.perform(
                get("/search/Amit")
                        .principal(
                                () -> "test@gmail.com"
                        )
        )
        .andExpect(
                status().isOk()
        )
        .andExpect(
                jsonPath("$.length()")
                        .value(1)
        )
        .andExpect(
                jsonPath("$[0].cid")
                        .value(10)
        )
        .andExpect(
                jsonPath("$[0].name")
                        .value("Amit")
        );
    }


    // =====================================================
    // 4. SEARCH - USER IS FETCHED
    // =====================================================

    @Test
    void searchUserTest()
            throws Exception {

        User user = new User();

        when(userRepository.getUserByName(
                "test@gmail.com"
        )).thenReturn(user);


        when(
                contactRepository
                        .findByNameContainingAndUser(
                                "Test",
                                user
                        )
        ).thenReturn(
                Collections.emptyList()
        );


        mockMvc.perform(
                get("/search/Test")
                        .principal(
                                () -> "test@gmail.com"
                        )
        )
        .andExpect(
                status().isOk()
        );


        org.mockito.Mockito.verify(
                userRepository
        ).getUserByName(
                "test@gmail.com"
        );
    }


    // =====================================================
    // 5. SEARCH - SPECIAL QUERY
    // =====================================================

    @Test
    void searchSpecialQueryTest()
            throws Exception {

        User user = new User();

        Contact contact = new Contact();

        contact.setCid(20);
        contact.setName("John Doe");

        when(userRepository.getUserByName(
                "test@gmail.com"
        )).thenReturn(user);


        when(
                contactRepository
                        .findByNameContainingAndUser(
                                "John Doe",
                                user
                        )
        ).thenReturn(
                Collections.singletonList(
                        contact
                )
        );


        mockMvc.perform(
                get("/search/John Doe")
                        .principal(
                                () -> "test@gmail.com"
                        )
        )
        .andExpect(
                status().isOk()
        )
        .andExpect(
                jsonPath("$.length()")
                        .value(1)
        )
        .andExpect(
                jsonPath("$[0].name")
                        .value("John Doe")
        );
    }


    // =====================================================
    // 6. DIRECT CONTROLLER TEST
    // =====================================================

    @Test
    void searchDirectTest() {

        SearchController controller =
                new SearchController();

        assertNotNull(controller);
    }
}