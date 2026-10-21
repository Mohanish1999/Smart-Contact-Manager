package com.smart.controller;

import java.security.Principal;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smart.Dao.UserRepository;
import com.smart.dto.ChangePasswordForm;
import com.smart.dto.ContactForm;
import com.smart.dto.ProfileForm;
import com.smart.entity.Contact;
import com.smart.entity.User;
import com.smart.exception.InvalidPasswordException;
import com.smart.exception.InvalidUploadException;
import com.smart.exception.ResourceNotFoundException;
import com.smart.exception.WeakPasswordException;
import com.smart.helper.Message;
import com.smart.service.ContactService;
import com.smart.service.PasswordPolicy;
import com.smart.service.UserProfileService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Registration plus everything a logged-in user can do. The controller only handles HTTP concerns
 * (binding, validation messages, redirects); the rules live in {@link ContactService} and
 * {@link UserProfileService}. The acting user always comes from the security context ({@link Principal}).
 */
@Controller
@RequestMapping("/user")
public class UserController {

    private static final String CONTACT_FORM_VIEW = "normal/add_contact_form";
    private static final String UPDATE_FORM_VIEW = "normal/update_form";
    private static final String SETTING_VIEW = "normal/setting";
    private static final String PROFILE_EDIT_VIEW = "normal/profile_edit";
    private static final String CONTACTS_REDIRECT = "redirect:/user/show-contacts";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final ContactService contactService;
    private final UserProfileService profileService;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy, ContactService contactService, UserProfileService profileService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.contactService = contactService;
        this.profileService = profileService;
    }

    /** Registration may only bind these fields: id, role, enabled and image can never come from the form. */
    @InitBinder("user")
    public void restrictRegistrationBinding(WebDataBinder binder) {
        binder.setAllowedFields("name", "email", "password", "about");
    }

    // ------------------------------------------------------------ registration

    @PostMapping("/do_register")
    public String registerUser(@Valid @ModelAttribute("user") User user, BindingResult result,
            @RequestParam(value = "agreement", defaultValue = "false") boolean agreement,
            Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("title", "Register-SmartContactManager");

        if (!agreement) {
            model.addAttribute("message", new Message("You must accept the terms and conditions", "danger"));
            return "signup";
        }
        if (!result.hasFieldErrors("password")) {
            passwordPolicy.check(user.getPassword())
                    .ifPresent(message -> result.rejectValue("password", "password.policy", message));
        }
        if (result.hasErrors()) {
            return "signup";
        }

        user.setEmail(user.getEmail().trim());
        if (userRepository.findByEmail(user.getEmail()) != null) {
            model.addAttribute("message", new Message("Email already exists", "danger"));
            return "signup";
        }
        try {
            user.setRole("ROLE_USER");
            user.setEnabled(true);
            user.setImageurl(null);
            if (user.getAbout() != null && user.getAbout().isBlank()) {
                user.setAbout(null);
            }
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("message", new Message("Email already exists", "danger"));
            return "signup";
        }
        redirectAttributes.addFlashAttribute("message",
                new Message("Registration successful. Please log in.", "success"));
        return "redirect:/signin";
    }

    // ------------------------------------------------------------ dashboard

    @RequestMapping("/index")
    public String dashboard(Principal principal, Model model) {
        model.addAttribute("title", "Dashboard - SmartContactManager");
        model.addAttribute("stats", contactService.stats(principal.getName()));
        return "normal/user_dashboard";
    }

    // ------------------------------------------------------------ contacts: add

    @GetMapping("/add-contact")
    public String addContactForm(Model model) {
        model.addAttribute("title", "Add Contact - SmartContactManager");
        model.addAttribute("contactForm", new ContactForm());
        return CONTACT_FORM_VIEW;
    }

    @PostMapping("/process-contact")
    public String processContact(@Valid @ModelAttribute("contactForm") ContactForm form, BindingResult result,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("title", "Add Contact - SmartContactManager");
        if (result.hasErrors()) {
            return CONTACT_FORM_VIEW;
        }
        try {
            contactService.add(principal.getName(), form, profileImage);
        } catch (InvalidUploadException e) {
            model.addAttribute("photoError", e.getMessage());
            return CONTACT_FORM_VIEW;
        }
        redirectAttributes.addFlashAttribute("message", new Message("Contact added successfully.", "success"));
        return CONTACTS_REDIRECT;
    }

    // ------------------------------------------------------------ contacts: list, search, pagination

    @GetMapping({ "/show-contacts", "/show-contacts/{page}" })
    public String showContacts(@PathVariable("page") Optional<Integer> page,
            @RequestParam(value = "q", required = false) String q,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        int currentPage = Math.max(page.orElse(0), 0);
        Page<Contact> result = contactService.list(principal.getName(), q, currentPage);

        if (result.getTotalPages() > 0 && currentPage >= result.getTotalPages()) {
            // e.g. the last contact of the last page was just deleted
            if (q != null && !q.isBlank()) {
                redirectAttributes.addAttribute("q", q);
            }
            return "redirect:/user/show-contacts/" + (result.getTotalPages() - 1);
        }
        model.addAttribute("title", "Your Contacts - SmartContactManager");
        model.addAttribute("contacts", result.getContent());
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("q", q == null ? "" : q.trim());
        return "normal/show_contacts";
    }

    // ------------------------------------------------------------ contacts: view, edit, delete

    @GetMapping("/{cid}/contact")
    public String showContactDetail(@PathVariable("cid") int cid, Principal principal, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("contact", contactService.getOwned(principal.getName(), cid));
        } catch (ResourceNotFoundException e) {
            return contactNotFound(redirectAttributes);
        }
        model.addAttribute("title", "Contact - SmartContactManager");
        return "normal/contact_detail";
    }

    @GetMapping("/update-contact/{cid}")
    public String updateContactForm(@PathVariable("cid") int cid, Principal principal, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Contact contact = contactService.getOwned(principal.getName(), cid);
            model.addAttribute("contact", contact);
            model.addAttribute("contactForm", ContactForm.from(contact));
        } catch (ResourceNotFoundException e) {
            return contactNotFound(redirectAttributes);
        }
        model.addAttribute("title", "Edit Contact - SmartContactManager");
        return UPDATE_FORM_VIEW;
    }

    @PostMapping("/update-contact/{cid}")
    public String processUpdate(@PathVariable("cid") int cid,
            @Valid @ModelAttribute("contactForm") ContactForm form, BindingResult result,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        Contact contact;
        try {
            contact = contactService.getOwned(principal.getName(), cid);
        } catch (ResourceNotFoundException e) {
            return contactNotFound(redirectAttributes);
        }
        model.addAttribute("title", "Edit Contact - SmartContactManager");
        model.addAttribute("contact", contact);
        if (result.hasErrors()) {
            return UPDATE_FORM_VIEW;
        }
        try {
            contactService.update(principal.getName(), cid, form, profileImage);
        } catch (InvalidUploadException e) {
            model.addAttribute("photoError", e.getMessage());
            return UPDATE_FORM_VIEW;
        }
        redirectAttributes.addFlashAttribute("message", new Message("Contact updated successfully.", "success"));
        return "redirect:/user/" + cid + "/contact";
    }

    /** POST only (CSRF-protected). Ownership is checked against the logged-in user. */
    @PostMapping("/delete/{cid}")
    public String deleteContact(@PathVariable("cid") int cid, Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            contactService.delete(principal.getName(), cid);
        } catch (ResourceNotFoundException e) {
            return contactNotFound(redirectAttributes);
        }
        redirectAttributes.addFlashAttribute("message", new Message("Contact deleted.", "success"));
        return CONTACTS_REDIRECT;
    }

    // ------------------------------------------------------------ profile

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        model.addAttribute("title", "Your Profile - SmartContactManager");
        model.addAttribute("contactCount", contactService.count(principal.getName()));
        return "normal/profile";
    }

    @GetMapping("/profile/edit")
    public String editProfileForm(Principal principal, Model model) {
        model.addAttribute("title", "Edit Profile - SmartContactManager");
        model.addAttribute("profileForm", ProfileForm.from(profileService.getByEmail(principal.getName())));
        return PROFILE_EDIT_VIEW;
    }

    @PostMapping("/profile/edit")
    public String processProfile(@Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult result,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("title", "Edit Profile - SmartContactManager");
        if (result.hasErrors()) {
            return PROFILE_EDIT_VIEW;
        }
        try {
            profileService.updateProfile(principal.getName(), form, profileImage);
        } catch (InvalidUploadException e) {
            model.addAttribute("photoError", e.getMessage());
            return PROFILE_EDIT_VIEW;
        }
        redirectAttributes.addFlashAttribute("message", new Message("Profile updated successfully.", "success"));
        return "redirect:/user/profile";
    }

    // ------------------------------------------------------------ settings: change password, delete account

    @GetMapping("/setting")
    public String settings(Model model) {
        model.addAttribute("title", "Settings - SmartContactManager");
        model.addAttribute("changePasswordForm", new ChangePasswordForm());
        model.addAttribute("passwordHint", passwordPolicy.describe());
        return SETTING_VIEW;
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute("changePasswordForm") ChangePasswordForm form,
            BindingResult result, Principal principal, Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("title", "Settings - SmartContactManager");
        model.addAttribute("passwordHint", passwordPolicy.describe());

        if (!result.hasFieldErrors("newPassword") && !result.hasFieldErrors("confirmPassword")
                && !form.getNewPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
        }
        if (result.hasErrors()) {
            return SETTING_VIEW;
        }
        try {
            profileService.changePassword(principal.getName(), form.getCurrentPassword(), form.getNewPassword());
        } catch (InvalidPasswordException e) {
            result.rejectValue("currentPassword", "password.wrong", e.getMessage());
            return SETTING_VIEW;
        } catch (WeakPasswordException e) {
            result.rejectValue("newPassword", "password.weak", e.getMessage());
            return SETTING_VIEW;
        }
        redirectAttributes.addFlashAttribute("message",
                new Message("Your password was changed. Use the new password next time you log in.", "success"));
        return "redirect:/user/setting";
    }

    /** Permanently deletes the logged-in user's own account after re-entering the password. */
    @PostMapping("/account/delete")
    public String deleteAccount(@RequestParam("currentPassword") String currentPassword,
            Authentication authentication, HttpServletRequest request, HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        try {
            profileService.deleteAccount(authentication.getName(), currentPassword);
        } catch (InvalidPasswordException e) {
            redirectAttributes.addFlashAttribute("message", new Message("Password is incorrect. Account not deleted.", "danger"));
            return "redirect:/user/profile";
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/signin?deleted";
    }

    // ------------------------------------------------------------ helpers

    private String contactNotFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("message", new Message("That contact could not be found.", "danger"));
        return CONTACTS_REDIRECT;
    }
}
