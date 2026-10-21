package com.smart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import com.smart.Dao.ContactRepository;
import com.smart.Dao.UserRepository;
import com.smart.dto.ContactForm;
import com.smart.dto.DashboardStats;
import com.smart.entity.Contact;
import com.smart.entity.User;
import com.smart.exception.InvalidUploadException;
import com.smart.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContactServiceTest {

    private static final String ASHA = "asha@example.com";
    private static final String BOB = "bob@example.com";

    @Mock
    private UserRepository users;
    @Mock
    private ContactRepository contacts;
    @Mock
    private FileStorageService storage;

    private ContactService service;
    private User asha;
    private User bob;

    @BeforeEach
    void setUp() {
        service = new ContactService(users, contacts, storage);
        asha = user(1, ASHA);
        bob = user(2, BOB);
        when(users.findByEmail(ASHA)).thenReturn(asha);
        when(users.findByEmail(BOB)).thenReturn(bob);
        when(contacts.saveAndFlush(any(Contact.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static User user(int id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private static ContactForm form(String name, String phone) {
        ContactForm f = new ContactForm();
        f.setName(name);
        f.setPhone(phone);
        return f;
    }

    private static Contact contactOf(User owner, int cid, String image) {
        Contact c = new Contact();
        c.setCid(cid);
        c.setName("Old Name");
        c.setPhone("1234567");
        c.setUser(owner);
        c.setImage(image);
        return c;
    }

    private MockMultipartFile photo() {
        return new MockMultipartFile("profileImage", "p.png", "image/png", new byte[] { 1, 2, 3 });
    }

    // ------------------------------------------------------------ add

    @Test
    void addSavesTheContactForTheLoggedInUserOnly() {
        ContactForm f = form("Rahul", "9876543210");
        f.setEmail("r@example.com");
        f.setWork("  ");

        Contact saved = service.add(ASHA, f, null);

        assertSame(asha, saved.getUser());
        assertEquals("Rahul", saved.getName());
        assertEquals("r@example.com", saved.getEmail());
        assertNull(saved.getWork(), "blank optional fields are stored as null");
        assertNull(saved.getImage());
    }

    @Test
    void addStoresThePhotoAndKeepsItsGeneratedName() {
        when(storage.store(any())).thenReturn("abc123.png");

        Contact saved = service.add(ASHA, form("Rahul", "9876543210"), photo());

        assertEquals("abc123.png", saved.getImage());
    }

    @Test
    void addWithARejectedPhotoSavesNothing() {
        when(storage.store(any())).thenThrow(new InvalidUploadException("Only JPG, PNG, GIF or WebP images are allowed."));

        assertThrows(InvalidUploadException.class, () -> service.add(ASHA, form("Rahul", "9876543210"), photo()));

        verify(contacts, never()).saveAndFlush(any(Contact.class));
    }

    @Test
    void addDeletesTheNewPhotoIfTheDatabaseSaveFails() {
        when(storage.store(any())).thenReturn("abc123.png");
        when(contacts.saveAndFlush(any(Contact.class))).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> service.add(ASHA, form("Rahul", "9876543210"), photo()));

        verify(storage).delete("abc123.png");
    }

    @Test
    void addRejectsAnUnknownUser() {
        assertThrows(ResourceNotFoundException.class, () -> service.add("ghost@example.com", form("A", "1234567"), null));
    }

    // ------------------------------------------------------------ ownership

    @Test
    void ownerCanReadHisContact() {
        Contact c = contactOf(asha, 10, null);
        when(contacts.findByCidAndUserId(10, 1)).thenReturn(Optional.of(c));

        assertSame(c, service.getOwned(ASHA, 10));
    }

    @Test
    void anotherUsersContactIsReportedAsNotFound() {
        when(contacts.findByCidAndUserId(10, 1)).thenReturn(Optional.of(contactOf(asha, 10, null)));
        when(contacts.findByCidAndUserId(10, 2)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getOwned(BOB, 10));
        assertThrows(ResourceNotFoundException.class, () -> service.update(BOB, 10, form("Hacked", "1234567"), null));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(BOB, 10));

        verify(contacts, never()).delete(any(Contact.class));
        verify(contacts, never()).saveAndFlush(any(Contact.class));
    }

    // ------------------------------------------------------------ update

    @Test
    void updateChangesTheFieldsAndKeepsTheOwner() {
        Contact c = contactOf(asha, 10, "old.png");
        when(contacts.findByCidAndUserId(10, 1)).thenReturn(Optional.of(c));
        ContactForm f = form("New Name", "9999999999");
        f.setEmail("");

        Contact updated = service.update(ASHA, 10, f, null);

        assertEquals("New Name", updated.getName());
        assertEquals("9999999999", updated.getPhone());
        assertNull(updated.getEmail());
        assertSame(asha, updated.getUser());
        assertEquals("old.png", updated.getImage(), "no new photo keeps the current one");
        verify(storage, never()).delete(anyString());
    }

    @Test
    void updateWithANewPhotoDeletesTheOldFile() {
        Contact c = contactOf(asha, 10, "old.png");
        when(contacts.findByCidAndUserId(10, 1)).thenReturn(Optional.of(c));
        when(storage.store(any())).thenReturn("new.png");

        service.update(ASHA, 10, form("Name", "1234567"), photo());

        assertEquals("new.png", c.getImage());
        verify(storage).delete("old.png");
    }

    @Test
    void updateWithARejectedPhotoKeepsTheOldOne() {
        Contact c = contactOf(asha, 10, "old.png");
        when(contacts.findByCidAndUserId(10, 1)).thenReturn(Optional.of(c));
        when(storage.store(any())).thenThrow(new InvalidUploadException("bad"));

        assertThrows(InvalidUploadException.class, () -> service.update(ASHA, 10, form("Name", "1234567"), photo()));

        assertEquals("old.png", c.getImage());
        verify(storage, never()).delete("old.png");
    }

    // ------------------------------------------------------------ delete

    @Test
    void deleteRemovesTheContactAndItsPhoto() {
        Contact c = contactOf(asha, 10, "photo.png");
        when(contacts.findByCidAndUserId(10, 1)).thenReturn(Optional.of(c));

        service.delete(ASHA, 10);

        verify(contacts).delete(c);
        verify(storage).delete("photo.png");
    }

    // ------------------------------------------------------------ list / search / pagination

    @Test
    void listWithoutAQueryUsesThePlainOwnerQuery() {
        Page<Contact> page = new PageImpl<>(List.of(contactOf(asha, 1, null)));
        when(contacts.findContactsByUser(eq(1), any(Pageable.class))).thenReturn(page);

        assertSame(page, service.list(ASHA, "   ", 0));

        verify(contacts, never()).searchContacts(anyInt(), anyString(), any(Pageable.class));
    }

    @Test
    void searchEscapesWildcardsAndIsScopedToTheOwner() {
        when(contacts.searchContacts(eq(1), anyString(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(ASHA, "  50%_!Ab ", 0);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(contacts).searchContacts(eq(1), pattern.capture(), any(Pageable.class));
        assertEquals("%50!%!_!!ab%", pattern.getValue());
    }

    @Test
    void paginationUsesTheConfiguredPageSizeAndClampsNegativePages() {
        when(contacts.findContactsByUser(eq(1), any(Pageable.class))).thenReturn(Page.empty());

        service.list(ASHA, null, -4);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(contacts).findContactsByUser(eq(1), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(ContactService.PAGE_SIZE, pageable.getValue().getPageSize());
        assertTrue(pageable.getValue() instanceof PageRequest);
    }

    @Test
    void anEmptyListIsAnEmptyPageNotAnError() {
        when(contacts.findContactsByUser(eq(2), any(Pageable.class))).thenReturn(Page.empty());

        Page<Contact> page = service.list(BOB, null, 0);

        assertEquals(0, page.getTotalPages());
        assertTrue(page.getContent().isEmpty());
    }

    // ------------------------------------------------------------ dashboard

    @Test
    void statsComeFromTheDatabaseForTheLoggedInUser() {
        List<Contact> recent = List.of(contactOf(asha, 3, null));
        when(contacts.countByUserId(1)).thenReturn(7L);
        when(contacts.countWithEmail(1)).thenReturn(5L);
        when(contacts.countWithPhoto(1)).thenReturn(2L);
        when(contacts.findTop5ByUserIdOrderByCidDesc(1)).thenReturn(recent);

        DashboardStats stats = service.stats(ASHA);

        assertEquals(7, stats.getTotalContacts());
        assertEquals(5, stats.getWithEmail());
        assertEquals(2, stats.getWithPhoto());
        assertSame(recent, stats.getRecent());
    }
}
