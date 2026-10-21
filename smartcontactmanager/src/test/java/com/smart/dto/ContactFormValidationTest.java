package com.smart.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class ContactFormValidationTest {

    private static ValidatorFactory factory;

    @BeforeAll
    static void start() {
        factory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void stop() {
        factory.close();
    }

    private static ContactForm valid() {
        ContactForm form = new ContactForm();
        form.setName("Asha Patil");
        form.setPhone("+91 98765 43210");
        return form;
    }

    private Set<String> invalidFields(ContactForm form) {
        return factory.getValidator().validate(form).stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void aMinimalContactIsValid() {
        assertTrue(invalidFields(valid()).isEmpty());
    }

    @Test
    void nameAndPhoneAreRequired() {
        ContactForm form = new ContactForm();
        assertEquals(Set.of("name", "phone"), invalidFields(form));
    }

    @Test
    void whitespaceOnlyCountsAsEmpty() {
        ContactForm form = valid();
        form.setName("    ");
        form.setPhone("   ");
        assertEquals(Set.of("name", "phone"), invalidFields(form));
    }

    @Test
    void validatesEmailFormat() {
        ContactForm form = valid();
        form.setEmail("not-an-email");
        assertEquals(Set.of("email"), invalidFields(form));
        form.setEmail("asha@example.com");
        assertTrue(invalidFields(form).isEmpty());
        form.setEmail("");
        assertTrue(invalidFields(form).isEmpty(), "email is optional");
    }

    @Test
    void validatesPhoneFormat() {
        for (String bad : new String[] { "abc", "12", "98765-abc", "++919876543210", "0".repeat(30) }) {
            ContactForm form = valid();
            form.setPhone(bad);
            assertEquals(Set.of("phone"), invalidFields(form), bad);
        }
        for (String good : new String[] { "9876543210", "+91 98765 43210", "(022) 2345-6789", "+1-555-123-4567" }) {
            ContactForm form = valid();
            form.setPhone(good);
            assertTrue(invalidFields(form).isEmpty(), good);
        }
    }

    @Test
    void validatesNameCharactersAndLength() {
        ContactForm form = valid();
        form.setName("<script>alert(1)</script>");
        assertEquals(Set.of("name"), invalidFields(form));
        form.setName("Zoë O'Brien-Smith Jr.");
        assertTrue(invalidFields(form).isEmpty());
        form.setName("A".repeat(51));
        assertEquals(Set.of("name"), invalidFields(form));
    }

    @Test
    void enforcesLengthLimitsOfTheOtherFields() {
        ContactForm form = valid();
        form.setSecondname("n".repeat(51));
        form.setWork("w".repeat(101));
        form.setDescription("d".repeat(2001));
        assertEquals(Set.of("secondname", "work", "description"), invalidFields(form));
    }

    @Test
    void theFormCannotCarryAnIdOrOwner() {
        for (var field : ContactForm.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertTrue(!name.equals("cid") && !name.equals("id") && !name.equals("user"), name);
        }
    }

    @Test
    void messagesAreHumanReadable() {
        ContactForm form = new ContactForm();
        Set<String> messages = factory.getValidator().validate(form).stream()
                .map(ConstraintViolation::getMessage).collect(Collectors.toSet());
        assertTrue(messages.contains("Name is required"));
        assertTrue(messages.contains("Phone number is required"));
    }
}
