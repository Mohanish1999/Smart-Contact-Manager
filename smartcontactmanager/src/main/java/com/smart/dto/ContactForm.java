package com.smart.dto;

import com.smart.entity.Contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What the browser may send when adding or editing a contact. It deliberately has no id and no user:
 * ownership is always taken from the logged-in user, never from the form.
 * Text is trimmed so that "   " counts as empty.
 */
public class ContactForm {

    private static final String NAME_REGEX = "^(\\p{L}[\\p{L} .'-]*)?$";
    private static final String PHONE_REGEX = "^(\\+?[0-9][0-9 ()-]{6,19})?$";

    @NotBlank(message = "Name is required")
    @Size(max = 50, message = "Name cannot exceed 50 characters")
    @Pattern(regexp = NAME_REGEX, message = "Name can contain letters, spaces and . ' - only")
    private String name;

    @Size(max = 50, message = "Nickname cannot exceed 50 characters")
    private String secondname;

    @Size(max = 100, message = "Work cannot exceed 100 characters")
    private String work;

    @Email(message = "Enter a valid email address")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = PHONE_REGEX, message = "Enter a valid phone number (7 to 20 digits, may start with +)")
    private String phone;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    public static ContactForm from(Contact contact) {
        ContactForm form = new ContactForm();
        form.setName(contact.getName());
        form.setSecondname(contact.getSecondname());
        form.setWork(contact.getWork());
        form.setEmail(contact.getEmail());
        form.setPhone(contact.getPhone());
        form.setDescription(contact.getDescription());
        return form;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = trim(name);
    }

    public String getSecondname() {
        return secondname;
    }

    public void setSecondname(String secondname) {
        this.secondname = trim(secondname);
    }

    public String getWork() {
        return work;
    }

    public void setWork(String work) {
        this.work = trim(work);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = trim(email);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = trim(phone);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = trim(description);
    }
}
