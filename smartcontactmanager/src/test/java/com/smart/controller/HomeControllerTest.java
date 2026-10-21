package com.smart.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@WebMvcTest(HomeController.class)
@AutoConfigureMockMvc(addFilters = false)
class HomeControllerTest {

    private HomeController homeController;

    private Model model;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        homeController = new HomeController();

        model = new ConcurrentModel();
    }

    @Test
    void homeTest() {

        String result = homeController.home(model);

        assertEquals("home", result);

        assertEquals(
            "Home-SmartContactManager",
            model.getAttribute("title")
        );
    }

    @Test
    void aboutTest() {

        String result = homeController.about(model);

        assertEquals("about", result);

        assertEquals(
            "About-SmartContactManager",
            model.getAttribute("title")
        );
    }

    @Test
    void signupTest() {

        String result = homeController.signup(model);

        assertEquals("signup", result);

        assertEquals(
            "Register-SmartContactManager",
            model.getAttribute("title")
        );

        assertNotNull(model.getAttribute("user"));
    }

//    @Test
//    void signinTest() {
//
//        String result = homeController.customLogin(model);
//
//        assertEquals("login", result);
//
//        assertEquals(
//            "Login Page",
//            model.getAttribute("title")
//        );
//    }

    @Test
    void homeRequestTest() throws Exception {

        mockMvc.perform(get("/"))
               .andExpect(status().isOk())
               .andExpect(view().name("home"))
               .andExpect(
                   MockMvcResultMatchers.model()
                       .attribute("title", "Home-SmartContactManager")
               );
    }

    @Test
    void aboutRequestTest() throws Exception {

        mockMvc.perform(get("/about"))
               .andExpect(status().isOk())
               .andExpect(view().name("about"))
               .andExpect(
                   MockMvcResultMatchers.model()
                       .attribute("title", "About-SmartContactManager")
               );
    }

    @Test
    void signupRequestTest() throws Exception {

        mockMvc.perform(get("/signup"))
               .andExpect(status().isOk())
               .andExpect(view().name("signup"))
               .andExpect(
                   MockMvcResultMatchers.model()
                       .attribute("title", "Register-SmartContactManager")
               )
               .andExpect(
                   MockMvcResultMatchers.model()
                       .attributeExists("user")
               );
    }

    @Test
    void signinRequestTest() throws Exception {

        mockMvc.perform(get("/signin"))
               .andExpect(status().isOk())
               .andExpect(view().name("login"))
               .andExpect(
                   MockMvcResultMatchers.model()
                       .attribute("title", "Login Page")
               );
    }
    
    
}