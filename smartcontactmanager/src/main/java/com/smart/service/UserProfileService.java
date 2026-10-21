package com.smart.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.smart.Dao.ContactRepository;
import com.smart.Dao.MyOrderRepository;
import com.smart.Dao.UserRepository;
import com.smart.dto.ProfileForm;
import com.smart.entity.User;
import com.smart.exception.InvalidPasswordException;
import com.smart.exception.ResourceNotFoundException;
import com.smart.exception.WeakPasswordException;

/** Profile, password and account use-cases for the logged-in user (identified by the security context only). */
@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserRepository users;
    private final ContactRepository contacts;
    private final MyOrderRepository orders;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final FileStorageService storage;

    public UserProfileService(UserRepository users, ContactRepository contacts, MyOrderRepository orders,
            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, FileStorageService storage) {
        this.users = users;
        this.contacts = contacts;
        this.orders = orders;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return requireUser(email);
    }

    /** Updates name, about and (optionally) the photo. Email, role, enabled and password cannot change here. */
    @Transactional
    public User updateProfile(String email, ProfileForm form, MultipartFile photo) {
        User user = requireUser(email);
        user.setName(form.getName());
        user.setAbout(form.getAbout() == null || form.getAbout().isBlank() ? null : form.getAbout());

        String oldImage = user.getImageurl();
        String newImage = null;
        if (FileStorageService.hasFile(photo)) {
            newImage = storage.store(photo);
            user.setImageurl(newImage);
        }
        try {
            User saved = users.saveAndFlush(user);
            if (newImage != null) {
                storage.delete(oldImage);
            }
            return saved;
        } catch (RuntimeException e) {
            storage.delete(newImage);
            throw e;
        }
    }

    /**
     * Changes the password after verifying the current one with the PasswordEncoder.
     * The new password is stored only as a hash.
     *
     * @throws InvalidPasswordException if the current password is wrong
     * @throws WeakPasswordException    if the new password breaks the policy or equals the old one
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = requireUser(email);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidPasswordException("Current password is incorrect.");
        }
        passwordPolicy.check(newPassword).ifPresent(message -> {
            throw new WeakPasswordException(message);
        });
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new WeakPasswordException("New password must be different from the current password.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        users.save(user);
        log.info("Password changed for user {}", user.getId());
    }

    /**
     * Permanently deletes the account and its contacts after re-checking the password.
     * Donation records are kept but detached from the user (foreign key safe).
     */
    @Transactional
    public void deleteAccount(String email, String currentPassword) {
        User user = requireUser(email);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidPasswordException("Password is incorrect.");
        }
        List<String> contactImages = contacts.findImageNamesByUserId(user.getId());
        String profileImage = user.getImageurl();

        orders.detachUser(user.getId());
        users.delete(user); // contacts are removed by the cascade on User.contacts
        users.flush();

        contactImages.forEach(storage::delete);
        storage.delete(profileImage);
        log.info("Account {} deleted", user.getId());
    }

    private User requireUser(String email) {
        User user = email == null ? null : users.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found.");
        }
        return user;
    }
}
