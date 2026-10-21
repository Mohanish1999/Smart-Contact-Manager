package com.smart.service;

import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.smart.Dao.ContactRepository;
import com.smart.Dao.UserRepository;
import com.smart.dto.ContactForm;
import com.smart.dto.DashboardStats;
import com.smart.entity.Contact;
import com.smart.entity.User;
import com.smart.exception.ResourceNotFoundException;

/**
 * Contact use-cases. Every method takes the logged-in user's email (from the security context) and
 * resolves the owner from it: ids coming from the browser are only used to pick a record
 * <em>among that user's own contacts</em>.
 */
@Service
public class ContactService {

    public static final int PAGE_SIZE = 6;
    private static final int MAX_QUERY_LENGTH = 100;

    private final UserRepository users;
    private final ContactRepository contacts;
    private final FileStorageService storage;

    public ContactService(UserRepository users, ContactRepository contacts, FileStorageService storage) {
        this.users = users;
        this.contacts = contacts;
        this.storage = storage;
    }

    @Transactional
    public Contact add(String email, ContactForm form, MultipartFile photo) {
        User owner = requireUser(email);
        Contact contact = new Contact();
        apply(form, contact);
        contact.setUser(owner);
        if (FileStorageService.hasFile(photo)) {
            contact.setImage(storage.store(photo));
        }
        try {
            return contacts.saveAndFlush(contact);
        } catch (RuntimeException e) {
            storage.delete(contact.getImage()); // do not leave an orphan photo behind
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<Contact> list(String email, String query, int page) {
        User owner = requireUser(email);
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.asc("name").ignoreCase()));
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return contacts.findContactsByUser(owner.getId(), pageable);
        }
        return contacts.searchContacts(owner.getId(), toLikePattern(trimmed), pageable);
    }

    @Transactional(readOnly = true)
    public Contact getOwned(String email, int cid) {
        User owner = requireUser(email);
        return contacts.findByCidAndUserId(cid, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found."));
    }

    @Transactional
    public Contact update(String email, int cid, ContactForm form, MultipartFile photo) {
        Contact contact = getOwned(email, cid);
        String oldImage = contact.getImage();
        apply(form, contact);

        String newImage = null;
        if (FileStorageService.hasFile(photo)) {
            newImage = storage.store(photo);
            contact.setImage(newImage);
        }
        try {
            Contact saved = contacts.saveAndFlush(contact);
            if (newImage != null) {
                storage.delete(oldImage);
            }
            return saved;
        } catch (RuntimeException e) {
            storage.delete(newImage);
            throw e;
        }
    }

    @Transactional
    public void delete(String email, int cid) {
        Contact contact = getOwned(email, cid);
        String image = contact.getImage();
        contacts.delete(contact);
        storage.delete(image);
    }

    @Transactional(readOnly = true)
    public long count(String email) {
        return contacts.countByUserId(requireUser(email).getId());
    }

    @Transactional(readOnly = true)
    public DashboardStats stats(String email) {
        int userId = requireUser(email).getId();
        return new DashboardStats(contacts.countByUserId(userId), contacts.countWithEmail(userId),
                contacts.countWithPhoto(userId), contacts.findTop5ByUserIdOrderByCidDesc(userId));
    }

    // ---------------------------------------------------------------- helpers

    private User requireUser(String email) {
        User user = email == null ? null : users.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found.");
        }
        return user;
    }

    private static void apply(ContactForm form, Contact contact) {
        contact.setName(form.getName());
        contact.setSecondname(blankToNull(form.getSecondname()));
        contact.setWork(blankToNull(form.getWork()));
        contact.setEmail(blankToNull(form.getEmail()));
        contact.setPhone(form.getPhone());
        contact.setDescription(blankToNull(form.getDescription()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Turns user input into a safe, lower-case LIKE pattern ('!' is the escape character). */
    static String toLikePattern(String query) {
        String limited = query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query;
        String escaped = limited.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
