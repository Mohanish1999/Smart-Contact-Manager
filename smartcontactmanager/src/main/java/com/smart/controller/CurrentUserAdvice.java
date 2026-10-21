package com.smart.controller;

import java.security.Principal;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.smart.Dao.UserRepository;
import com.smart.entity.User;

/**
 * Makes the logged-in user available to every page of {@link UserController} as {@code currentUser}
 * (sidebar, welcome text, profile). It is named differently from the registration form's "user"
 * attribute on purpose, so a form can never be bound onto the real account.
 */
@ControllerAdvice(assignableTypes = UserController.class)
public class CurrentUserAdvice {

    // ObjectProvider keeps web-layer slice tests of unrelated controllers working without a UserRepository.
    private final ObjectProvider<UserRepository> userRepository;

    public CurrentUserAdvice(ObjectProvider<UserRepository> userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute("currentUser")
    public User currentUser(Principal principal) {
        UserRepository repository = userRepository.getIfAvailable();
        return principal == null || repository == null ? null : repository.findByEmail(principal.getName());
    }
}
