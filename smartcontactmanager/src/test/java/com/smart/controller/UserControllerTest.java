package com.smart.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.smart.Dao.UserRepository;
import com.smart.dto.ContactForm;
import com.smart.dto.DashboardStats;
import com.smart.dto.ProfileForm;
import com.smart.entity.Contact;
import com.smart.entity.User;
import com.smart.exception.InvalidPasswordException;
import com.smart.exception.InvalidUploadException;
import com.smart.exception.ResourceNotFoundException;
import com.smart.exception.WeakPasswordException;
import com.smart.service.ContactService;
import com.smart.service.PasswordPolicy;
import com.smart.service.UserProfileService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PasswordPolicy.class)
class UserControllerTest {

    private static final String EMAIL = "asha@example.com";
    private static final Principal ASHA = () -> EMAIL;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;
    @MockBean
    private PasswordEncoder passwordEncoder;
    @MockBean
    private ContactService contactService;
    @MockBean
    private UserProfileService profileService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenAnswer(i -> "ENC:" + i.getArgument(0));
    }

    private static Contact contact(int cid, String name) {
        Contact c = new Contact();
        c.setCid(cid);
        c.setName(name);
        c.setPhone("9876543210");
        return c;
    }

    // ================================================================= registration

    @Test
    void registrationSavesAHashedPasswordAndForcesUserRole() throws Exception {
        mockMvc.perform(post("/user/do_register").param("name", "Asha Patil").param("email", "asha@example.com")
                .param("password", "Passw0rdX").param("agreement", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signin"))
                .andExpect(flash().attributeExists("message"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("ENC:Passw0rdX", saved.getValue().getPassword());
        assertEquals("ROLE_USER", saved.getValue().getRole());
        assertTrue(saved.getValue().isEnabled());
    }

    @Test
    void registrationIgnoresAttemptsToSetIdRoleOrEnabled() throws Exception {
        mockMvc.perform(post("/user/do_register").param("name", "Mallory").param("email", "m@example.com")
                .param("password", "Passw0rdX").param("agreement", "true")
                .param("id", "1").param("role", "ROLE_ADMIN").param("enabled", "false").param("imageurl", "x.png"))
                .andExpect(redirectedUrl("/signin"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(0, saved.getValue().getId(), "a posted id must not overwrite another user");
        assertEquals("ROLE_USER", saved.getValue().getRole());
        assertTrue(saved.getValue().isEnabled());
        assertNull(saved.getValue().getImageurl());
    }

    @Test
    void registrationRejectsAWeakPasswordWithAFieldError() throws Exception {
        mockMvc.perform(post("/user/do_register").param("name", "Asha Patil").param("email", "a@example.com")
                .param("password", "abcdefgh").param("agreement", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeHasFieldErrors("user", "password"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registrationNeedsTheTermsToBeAccepted() throws Exception {
        mockMvc.perform(post("/user/do_register").param("name", "Asha Patil").param("email", "a@example.com")
                .param("password", "Passw0rdX"))
                .andExpect(view().name("signup"))
                .andExpect(model().attributeExists("message"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registrationRejectsAnEmailThatAlreadyExists() throws Exception {
        when(userRepository.findByEmail("asha@example.com")).thenReturn(new User());

        mockMvc.perform(post("/user/do_register").param("name", "Asha Patil").param("email", "asha@example.com")
                .param("password", "Passw0rdX").param("agreement", "true"))
                .andExpect(view().name("signup"))
                .andExpect(model().attributeExists("message"));
        verify(userRepository, never()).save(any(User.class));
    }

    // ================================================================= dashboard

    @Test
    void dashboardShowsRealStatistics() throws Exception {
        when(contactService.stats(EMAIL)).thenReturn(new DashboardStats(3, 2, 1, List.of(contact(1, "Rahul"))));

        mockMvc.perform(get("/user/index").principal(ASHA))
                .andExpect(status().isOk())
                .andExpect(view().name("normal/user_dashboard"))
                .andExpect(model().attributeExists("stats"));
    }

    // ================================================================= add contact

    @Test
    void addContactFormOpensWithAnEmptyForm() throws Exception {
        mockMvc.perform(get("/user/add-contact").principal(ASHA))
                .andExpect(status().isOk())
                .andExpect(view().name("normal/add_contact_form"))
                .andExpect(model().attributeExists("contactForm"));
    }

    @Test
    void validContactIsSavedForTheLoggedInUserAndWeRedirectToTheList() throws Exception {
        mockMvc.perform(multipart("/user/process-contact")
                .file(new MockMultipartFile("profileImage", "me.png", "image/png", new byte[] { 1, 2, 3 }))
                .param("name", "Rahul Patil").param("phone", "9876543210").param("email", "r@example.com")
                .principal(ASHA))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/show-contacts"))
                .andExpect(flash().attributeExists("message"));

        ArgumentCaptor<ContactForm> form = ArgumentCaptor.forClass(ContactForm.class);
        verify(contactService).add(eq(EMAIL), form.capture(), any());
        assertEquals("Rahul Patil", form.getValue().getName());
    }

    @Test
    void anOwnerOrIdPostedInTheFormIsIgnored() throws Exception {
        mockMvc.perform(post("/user/process-contact").principal(ASHA)
                .param("name", "Rahul").param("phone", "9876543210")
                .param("cid", "99").param("user.id", "2").param("user", "2"))
                .andExpect(redirectedUrl("/user/show-contacts"));

        // The service is always called with the identity from the security context.
        verify(contactService).add(eq(EMAIL), any(ContactForm.class), isNull());
    }

    @Test
    void invalidContactReShowsTheFormWithFieldErrorsAndSavesNothing() throws Exception {
        mockMvc.perform(post("/user/process-contact").principal(ASHA)
                .param("name", "").param("phone", "abc").param("email", "bad"))
                .andExpect(status().isOk())
                .andExpect(view().name("normal/add_contact_form"))
                .andExpect(model().attributeHasFieldErrors("contactForm", "name", "phone", "email"));

        verify(contactService, never()).add(anyString(), any(ContactForm.class), any());
    }

    @Test
    void aRejectedPhotoIsShownBesideTheField() throws Exception {
        when(contactService.add(eq(EMAIL), any(ContactForm.class), any()))
                .thenThrow(new InvalidUploadException("Only JPG, PNG, GIF or WebP images are allowed."));

        mockMvc.perform(post("/user/process-contact").principal(ASHA).param("name", "Rahul").param("phone", "9876543210"))
                .andExpect(view().name("normal/add_contact_form"))
                .andExpect(model().attribute("photoError", "Only JPG, PNG, GIF or WebP images are allowed."));
    }

    // ================================================================= list, search, pagination

    @Test
    void contactListShowsTheUsersContactsWithPagingInformation() throws Exception {
        when(contactService.list(EMAIL, null, 0)).thenReturn(
                new PageImpl<>(List.of(contact(1, "Rahul"), contact(2, "Sneha")), PageRequest.of(0, 6), 8));

        mockMvc.perform(get("/user/show-contacts").principal(ASHA))
                .andExpect(status().isOk())
                .andExpect(view().name("normal/show_contacts"))
                .andExpect(model().attribute("currentPage", 0))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(model().attribute("totalElements", 8L))
                .andExpect(model().attribute("contacts", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void anEmptyListRendersWithZeroPagesWithoutError() throws Exception {
        when(contactService.list(EMAIL, null, 0)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 6), 0));

        mockMvc.perform(get("/user/show-contacts").principal(ASHA))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalPages", 0))
                .andExpect(model().attribute("contacts", org.hamcrest.Matchers.empty()));
    }

    @Test
    void searchPassesTheQueryAndKeepsItForTheTemplate() throws Exception {
        when(contactService.list(EMAIL, "rahul", 1)).thenReturn(
                new PageImpl<>(List.of(contact(1, "Rahul")), PageRequest.of(1, 6), 7));

        mockMvc.perform(get("/user/show-contacts/1").param("q", "rahul").principal(ASHA))
                .andExpect(status().isOk())
                .andExpect(model().attribute("q", "rahul"))
                .andExpect(model().attribute("currentPage", 1));
    }

    @Test
    void aPageBeyondTheEndRedirectsToTheLastPage() throws Exception {
        when(contactService.list(EMAIL, null, 5)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(5, 6), 7));

        mockMvc.perform(get("/user/show-contacts/5").principal(ASHA))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/show-contacts/1"));
    }

    // ================================================================= view / edit / delete

    @Test
    void contactDetailShowsTheOwnedContact() throws Exception {
        when(contactService.getOwned(EMAIL, 7)).thenReturn(contact(7, "Rahul"));

        mockMvc.perform(get("/user/7/contact").principal(ASHA))
                .andExpect(status().isOk())
                .andExpect(view().name("normal/contact_detail"))
                .andExpect(model().attributeExists("contact"));
    }

    @Test
    void someoneElsesContactRedirectsToTheListWithAnError() throws Exception {
        when(contactService.getOwned(EMAIL, 8)).thenThrow(new ResourceNotFoundException("Contact not found."));

        mockMvc.perform(get("/user/8/contact").principal(ASHA))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/show-contacts"))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    void editFormIsPrefilledFromTheOwnedContact() throws Exception {
        Contact c = contact(7, "Rahul");
        c.setEmail("r@example.com");
        when(contactService.getOwned(EMAIL, 7)).thenReturn(c);

        mockMvc.perform(get("/user/update-contact/7").principal(ASHA))
                .andExpect(status().isOk())
                .andExpect(view().name("normal/update_form"))
                .andExpect(model().attribute("contactForm", org.hamcrest.Matchers.hasProperty("email", org.hamcrest.Matchers.is("r@example.com"))));
    }

    @Test
    void updateSavesThroughTheServiceAndReturnsToTheContact() throws Exception {
        when(contactService.getOwned(EMAIL, 7)).thenReturn(contact(7, "Rahul"));

        mockMvc.perform(post("/user/update-contact/7").principal(ASHA).param("name", "Rahul P").param("phone", "9999999999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/7/contact"));

        verify(contactService).update(eq(EMAIL), eq(7), any(ContactForm.class), isNull());
    }

    @Test
    void updateWithInvalidDataReShowsTheFormAndDoesNotSave() throws Exception {
        when(contactService.getOwned(EMAIL, 7)).thenReturn(contact(7, "Rahul"));

        mockMvc.perform(post("/user/update-contact/7").principal(ASHA).param("name", "").param("phone", "9999999999"))
                .andExpect(view().name("normal/update_form"))
                .andExpect(model().attributeHasFieldErrors("contactForm", "name"));
        verify(contactService, never()).update(anyString(), anyInt(), any(ContactForm.class), any());
    }

    @Test
    void updatingSomeoneElsesContactIsRefused() throws Exception {
        when(contactService.getOwned(EMAIL, 8)).thenThrow(new ResourceNotFoundException("Contact not found."));

        mockMvc.perform(post("/user/update-contact/8").principal(ASHA).param("name", "Hacked").param("phone", "9999999999"))
                .andExpect(redirectedUrl("/user/show-contacts"));
        verify(contactService, never()).update(anyString(), anyInt(), any(ContactForm.class), any());
    }

    @Test
    void deleteIsPostOnlyAndUsesTheLoggedInUser() throws Exception {
        mockMvc.perform(get("/user/delete/7").principal(ASHA)).andExpect(status().isMethodNotAllowed());
        verify(contactService, never()).delete(anyString(), anyInt());

        mockMvc.perform(post("/user/delete/7").principal(ASHA))
                .andExpect(redirectedUrl("/user/show-contacts"))
                .andExpect(flash().attributeExists("message"));
        verify(contactService).delete(EMAIL, 7);
    }

    @Test
    void deletingSomeoneElsesContactReportsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Contact not found.")).when(contactService).delete(EMAIL, 8);

        mockMvc.perform(post("/user/delete/8").principal(ASHA))
                .andExpect(redirectedUrl("/user/show-contacts"))
                .andExpect(flash().attributeExists("message"));
    }

    // ================================================================= profile

    @Test
    void profilePageShowsTheLoggedInUsersData() throws Exception {
        User asha = new User();
        asha.setEmail(EMAIL);
        asha.setName("Asha");
        when(userRepository.findByEmail(EMAIL)).thenReturn(asha);
        when(contactService.count(EMAIL)).thenReturn(4L);

        mockMvc.perform(get("/user/profile").principal(ASHA))
                .andExpect(status().isOk())
                .andExpect(view().name("normal/profile"))
                .andExpect(model().attribute("currentUser", asha))
                .andExpect(model().attribute("contactCount", 4L));
    }

    @Test
    void editProfileFormIsPrefilled() throws Exception {
        User asha = new User();
        asha.setName("Asha");
        asha.setAbout("Hi");
        when(profileService.getByEmail(EMAIL)).thenReturn(asha);

        mockMvc.perform(get("/user/profile/edit").principal(ASHA))
                .andExpect(view().name("normal/profile_edit"))
                .andExpect(model().attribute("profileForm", org.hamcrest.Matchers.hasProperty("name", org.hamcrest.Matchers.is("Asha"))));
    }

    @Test
    void validProfileEditIsSavedForTheLoggedInUser() throws Exception {
        mockMvc.perform(post("/user/profile/edit").principal(ASHA).param("name", "Asha P").param("about", "Hello")
                .param("email", "attacker@example.com").param("role", "ROLE_ADMIN"))
                .andExpect(redirectedUrl("/user/profile"))
                .andExpect(flash().attributeExists("message"));

        ArgumentCaptor<ProfileForm> form = ArgumentCaptor.forClass(ProfileForm.class);
        verify(profileService).updateProfile(eq(EMAIL), form.capture(), isNull());
        assertEquals("Asha P", form.getValue().getName());
    }

    @Test
    void invalidProfileEditReShowsTheForm() throws Exception {
        mockMvc.perform(post("/user/profile/edit").principal(ASHA).param("name", "A").param("about", "x".repeat(501)))
                .andExpect(view().name("normal/profile_edit"))
                .andExpect(model().attributeHasFieldErrors("profileForm", "name", "about"));
        verify(profileService, never()).updateProfile(anyString(), any(ProfileForm.class), any());
    }

    // ================================================================= settings / change password

    @Test
    void settingsPageOpensWithTheChangePasswordForm() throws Exception {
        mockMvc.perform(get("/user/setting").principal(ASHA))
                .andExpect(status().isOk())
                .andExpect(view().name("normal/setting"))
                .andExpect(model().attributeExists("changePasswordForm", "passwordHint"));
    }

    @Test
    void changePasswordCallsTheServiceWithTheLoggedInUser() throws Exception {
        mockMvc.perform(post("/user/change-password").principal(ASHA)
                .param("currentPassword", "Oldpass1").param("newPassword", "Newpass12").param("confirmPassword", "Newpass12"))
                .andExpect(redirectedUrl("/user/setting"))
                .andExpect(flash().attributeExists("message"));

        verify(profileService).changePassword(EMAIL, "Oldpass1", "Newpass12");
    }

    @Test
    void changePasswordRejectsAMismatchedConfirmation() throws Exception {
        mockMvc.perform(post("/user/change-password").principal(ASHA)
                .param("currentPassword", "Oldpass1").param("newPassword", "Newpass12").param("confirmPassword", "Different1"))
                .andExpect(view().name("normal/setting"))
                .andExpect(model().attributeHasFieldErrors("changePasswordForm", "confirmPassword"));
        verify(profileService, never()).changePassword(anyString(), anyString(), anyString());
    }

    @Test
    void changePasswordRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/user/change-password").principal(ASHA))
                .andExpect(view().name("normal/setting"))
                .andExpect(model().attributeHasFieldErrors("changePasswordForm", "currentPassword", "newPassword", "confirmPassword"));
    }

    @Test
    void changePasswordShowsAWrongCurrentPasswordBesideTheField() throws Exception {
        doThrow(new InvalidPasswordException("Current password is incorrect."))
                .when(profileService).changePassword(EMAIL, "WRONG", "Newpass12");

        mockMvc.perform(post("/user/change-password").principal(ASHA)
                .param("currentPassword", "WRONG").param("newPassword", "Newpass12").param("confirmPassword", "Newpass12"))
                .andExpect(view().name("normal/setting"))
                .andExpect(model().attributeHasFieldErrors("changePasswordForm", "currentPassword"));
    }

    @Test
    void changePasswordShowsPolicyViolationsBesideTheField() throws Exception {
        doThrow(new WeakPasswordException("Password must contain a number."))
                .when(profileService).changePassword(EMAIL, "Oldpass1", "abcdefghij");

        mockMvc.perform(post("/user/change-password").principal(ASHA)
                .param("currentPassword", "Oldpass1").param("newPassword", "abcdefghij").param("confirmPassword", "abcdefghij"))
                .andExpect(view().name("normal/setting"))
                .andExpect(model().attributeHasFieldErrors("changePasswordForm", "newPassword"));
    }

    // ================================================================= delete account

    @Test
    void deleteAccountWithAWrongPasswordKeepsTheAccount() throws Exception {
        doThrow(new InvalidPasswordException("Password is incorrect.")).when(profileService).deleteAccount(EMAIL, "nope");

        mockMvc.perform(post("/user/account/delete").principal(new TestingAuthenticationToken(EMAIL, "x"))
                .param("currentPassword", "nope"))
                .andExpect(redirectedUrl("/user/profile"))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    void deleteAccountWithTheRightPasswordLogsOutAndGoesToLogin() throws Exception {
        mockMvc.perform(post("/user/account/delete").principal(new TestingAuthenticationToken(EMAIL, "x"))
                .param("currentPassword", "Oldpass1"))
                .andExpect(redirectedUrl("/signin?deleted"));

        verify(profileService).deleteAccount(EMAIL, "Oldpass1");
    }
}
