package com.smart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.smart.Dao.ContactRepository;
import com.smart.Dao.MyOrderRepository;
import com.smart.Dao.UserRepository;
import com.smart.dto.ProfileForm;
import com.smart.entity.User;
import com.smart.exception.InvalidPasswordException;
import com.smart.exception.InvalidUploadException;
import com.smart.exception.WeakPasswordException;

/** Uses the REAL BCrypt encoder, so these tests prove what actually gets stored. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceTest {

    private static final String EMAIL = "asha@example.com";

    @Mock
    private UserRepository users;
    @Mock
    private ContactRepository contacts;
    @Mock
    private MyOrderRepository orders;
    @Mock
    private FileStorageService storage;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserProfileService service;
    private User asha;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(users, contacts, orders, encoder, new PasswordPolicy(8), storage);
        asha = new User();
        asha.setId(1);
        asha.setName("Asha");
        asha.setEmail(EMAIL);
        asha.setRole("ROLE_USER");
        asha.setEnabled(true);
        asha.setPassword(encoder.encode("Oldpass1"));
        when(users.findByEmail(EMAIL)).thenReturn(asha);
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    private MockMultipartFile photo() {
        return new MockMultipartFile("profileImage", "me.png", "image/png", new byte[] { 1, 2, 3 });
    }

    // ------------------------------------------------------------ change password

    @Test
    void changePasswordStoresOnlyABcryptHashThatLetsTheUserLogInWithTheNewPassword() {
        service.changePassword(EMAIL, "Oldpass1", "Newpass12");

        assertTrue(asha.getPassword().startsWith("$2"), "BCrypt hash expected");
        assertFalse(asha.getPassword().contains("Newpass12"));
        assertTrue(encoder.matches("Newpass12", asha.getPassword()), "new password must work for login");
        assertFalse(encoder.matches("Oldpass1", asha.getPassword()), "old password must stop working");
        verify(users).save(asha);
    }

    @Test
    void wrongCurrentPasswordIsRejectedAndNothingChanges() {
        String before = asha.getPassword();

        assertThrows(InvalidPasswordException.class, () -> service.changePassword(EMAIL, "WRONG", "Newpass12"));
        assertThrows(InvalidPasswordException.class, () -> service.changePassword(EMAIL, null, "Newpass12"));

        assertEquals(before, asha.getPassword());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void passwordsBreakingThePolicyAreRejected() {
        String before = asha.getPassword();

        for (String weak : new String[] { "short1", "abcdefghij", "1234567890", "" }) {
            assertThrows(WeakPasswordException.class, () -> service.changePassword(EMAIL, "Oldpass1", weak), weak);
        }
        assertEquals(before, asha.getPassword());
    }

    @Test
    void theNewPasswordMustDifferFromTheCurrentOne() {
        assertThrows(WeakPasswordException.class, () -> service.changePassword(EMAIL, "Oldpass1", "Oldpass1"));
    }

    // ------------------------------------------------------------ profile

    @Test
    void updateProfileChangesOnlyNameAndAbout() {
        String email = asha.getEmail();
        String role = asha.getRole();
        String hash = asha.getPassword();
        ProfileForm form = new ProfileForm();
        form.setName("  New Name ");
        form.setAbout("  Hello ");

        service.updateProfile(EMAIL, form, null);

        assertEquals("New Name", asha.getName());
        assertEquals("Hello", asha.getAbout());
        assertEquals(email, asha.getEmail());
        assertEquals(role, asha.getRole());
        assertEquals(hash, asha.getPassword());
        assertTrue(asha.isEnabled());
    }

    @Test
    void profileFormOffersNothingBeyondNameAndAbout() {
        List<String> fields = java.util.Arrays.stream(ProfileForm.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).sorted().toList();
        assertEquals(List.of("about", "name"), fields);
    }

    @Test
    void blankAboutIsStoredAsNull() {
        ProfileForm form = new ProfileForm();
        form.setName("Asha");
        form.setAbout("   ");

        service.updateProfile(EMAIL, form, null);

        assertNull(asha.getAbout());
    }

    @Test
    void newProfilePhotoReplacesAndDeletesTheOldFile() {
        asha.setImageurl("old.png");
        when(storage.store(any())).thenReturn("new.png");
        ProfileForm form = new ProfileForm();
        form.setName("Asha");

        service.updateProfile(EMAIL, form, photo());

        assertEquals("new.png", asha.getImageurl());
        verify(storage).delete("old.png");
    }

    @Test
    void rejectedProfilePhotoKeepsTheOldOne() {
        asha.setImageurl("old.png");
        when(storage.store(any())).thenThrow(new InvalidUploadException("bad"));
        ProfileForm form = new ProfileForm();
        form.setName("Asha");

        assertThrows(InvalidUploadException.class, () -> service.updateProfile(EMAIL, form, photo()));

        assertEquals("old.png", asha.getImageurl());
    }

    // ------------------------------------------------------------ delete account

    @Test
    void deleteAccountNeedsTheCorrectPassword() {
        assertThrows(InvalidPasswordException.class, () -> service.deleteAccount(EMAIL, "nope"));

        verify(users, never()).delete(any(User.class));
        verify(orders, never()).detachUser(1);
    }

    @Test
    void deleteAccountKeepsDonationRecordsAndRemovesPhotos() {
        asha.setImageurl("me.png");
        when(contacts.findImageNamesByUserId(1)).thenReturn(List.of("c1.png", "c2.png"));

        service.deleteAccount(EMAIL, "Oldpass1");

        InOrder order = inOrder(orders, users);
        order.verify(orders).detachUser(1);       // orders must stop pointing at the user first (foreign key)
        order.verify(users).delete(asha);
        verify(storage).delete("c1.png");
        verify(storage).delete("c2.png");
        verify(storage).delete("me.png");
    }
}
