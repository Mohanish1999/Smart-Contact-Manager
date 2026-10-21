package com.smart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.smart.Dao.ContactRepository;
import com.smart.Dao.MyOrderRepository;
import com.smart.Dao.UserRepository;
import com.smart.entity.Contact;
import com.smart.entity.MyOrder;
import com.smart.entity.User;
import com.smart.payment.GatewayOrder;
import com.smart.payment.PaymentGateway;
import com.smart.payment.RazorpaySignature;
import com.smart.service.EmailService;

/**
 * Drives the whole application through its real security filter chain (CSRF ON), real Thymeleaf templates,
 * real BCrypt and an in-memory H2 database (profile "test"). Email and Razorpay are mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndToEndFlowTest {

    private static final String KEY_SECRET = "test_secret_not_real";
    private static final String WEBHOOK_SECRET = "whsec_test_not_real";
    private static final String PASSWORD = "Passw0rdX";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private ContactRepository contacts;
    @Autowired
    private MyOrderRepository orders;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailService emailService;
    @MockBean
    private PaymentGateway gateway;

    private final AtomicInteger orderSeq = new AtomicInteger();

    // ------------------------------------------------------------------ helpers

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private User register(String email) throws Exception {
        mockMvc.perform(post("/user/do_register").with(csrf()).param("name", "Asha Patil").param("email", email)
                .param("password", PASSWORD).param("agreement", "true"))
                .andExpect(status().is3xxRedirection());
        User user = users.findByEmail(email);
        assertNotNull(user, "registration must create the user");
        return user;
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(formLogin("/dologin").user(email).password(password)).andReturn();
        assertEquals("/user/index", result.getResponse().getRedirectedUrl(), "login should succeed");
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String loginRedirect(String email, String password) throws Exception {
        return mockMvc.perform(formLogin("/dologin").user(email).password(password)).andReturn().getResponse().getRedirectedUrl();
    }

    private static byte[] png() {
        byte[] bytes = new byte[64];
        int[] sig = { 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
        for (int i = 0; i < sig.length; i++) {
            bytes[i] = (byte) sig[i];
        }
        return bytes;
    }

    private MvcResult addContact(MockHttpSession session, String name, MockMultipartFile photo) throws Exception {
        var request = multipart("/user/process-contact").with(csrf()).session(session)
                .param("name", name).param("phone", "9876543210").param("email", "c@example.com");
        if (photo != null) {
            ((MockMultipartHttpServletRequestBuilder) request).file(photo);
        }
        return mockMvc.perform(request).andReturn();
    }

    private Contact contactOf(User owner, String name) {
        return contacts.findAll().stream()
                .filter(c -> c.getUser() != null && c.getUser().getId() == owner.getId() && name.equals(c.getName()))
                .findFirst().orElse(null);
    }

    private String body(MockHttpSession session, String url) throws Exception {
        return mockMvc.perform(get(url).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------ registration, login, authorization

    @Test
    void registrationStoresABcryptHashAndCannotOverwriteAnotherUser() throws Exception {
        User first = register(uniqueEmail());
        assertTrue(first.getPassword().startsWith("$2"), "stored password must be a BCrypt hash");
        assertNotEquals(PASSWORD, first.getPassword());
        assertEquals("ROLE_USER", first.getRole());

        // Mass-assignment attempt: post the first user's id, an admin role and a different email.
        String attackerEmail = uniqueEmail();
        mockMvc.perform(post("/user/do_register").with(csrf()).param("name", "Mallory").param("email", attackerEmail)
                .param("password", PASSWORD).param("agreement", "true")
                .param("id", String.valueOf(first.getId())).param("role", "ROLE_ADMIN"));

        User reloaded = users.findByEmail(first.getEmail());
        assertNotNull(reloaded, "the first user must still exist under the same email");
        assertEquals(first.getPassword(), reloaded.getPassword());
        User attacker = users.findByEmail(attackerEmail);
        assertNotEquals(first.getId(), attacker.getId());
        assertEquals("ROLE_USER", attacker.getRole());
    }

    @Test
    void registrationRejectsWeakPasswordsAndDuplicateEmails() throws Exception {
        String email = uniqueEmail();
        String weak = mockMvc.perform(post("/user/do_register").with(csrf()).param("name", "Asha Patil").param("email", email)
                .param("password", "abcdefgh").param("agreement", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(weak.contains("Password must contain"));
        assertEquals(null, users.findByEmail(email));

        register(email);
        String duplicate = mockMvc.perform(post("/user/do_register").with(csrf()).param("name", "Other Person").param("email", email)
                .param("password", PASSWORD).param("agreement", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(duplicate.contains("Email already exists"));
    }

    @Test
    void protectedPagesNeedLoginAndWrongPasswordsAreRejected() throws Exception {
        String email = uniqueEmail();
        register(email);

        for (String url : List.of("/user/index", "/user/show-contacts", "/user/add-contact", "/user/profile", "/user/setting")) {
            String location = mockMvc.perform(get(url)).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
            assertTrue(location.endsWith("/signin"), url + " should redirect to login but went to " + location);
        }
        assertEquals("/signin?error", loginRedirect(email, "WrongPassw0rd"));
        assertTrue(body(login(email, PASSWORD), "/user/index").contains("Welcome back"));
    }

    @Test
    void publicPagesRenderWithTheNewDesign() throws Exception {
        for (String url : List.of("/", "/about", "/signin", "/signup", "/forgot")) {
            String html = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("/css/style.css"), url);
            assertFalse(html.contains("cdn.jsdelivr") || html.contains("unpkg.com"), url + " must not depend on a CDN");
        }
    }

    @Test
    void csrfProtectionIsEnforcedOnStateChangingRequests() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);

        mockMvc.perform(post("/user/process-contact").session(session).param("name", "X").param("phone", "9876543210"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/user/change-password").session(session).param("currentPassword", PASSWORD)
                .param("newPassword", "Newpass12").param("confirmPassword", "Newpass12"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/user/delete/1").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        assertTrue(passwordEncoder.matches(PASSWORD, users.findByEmail(email).getPassword()), "password must be unchanged");
    }

    @Test
    void logoutWorksWithPostAndEndsTheSession() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);

        String location = mockMvc.perform(post("/logout").with(csrf()).session(session)).andReturn().getResponse().getRedirectedUrl();
        assertEquals("/signin?logout", location);
        mockMvc.perform(get("/user/index").session(session)).andExpect(status().is3xxRedirection());
    }

    // ------------------------------------------------------------------ contacts

    @Test
    void anAddedContactIsSavedForTheOwnerAndShowsUpInTheList() throws Exception {
        String email = uniqueEmail();
        User owner = register(email);
        MockHttpSession session = login(email, PASSWORD);

        String empty = body(session, "/user/show-contacts");
        assertTrue(empty.contains("No contacts yet"), "empty state expected");

        MvcResult result = addContact(session, "Rahul Patil", null);
        assertEquals("/user/show-contacts", result.getResponse().getRedirectedUrl());

        Contact saved = contactOf(owner, "Rahul Patil");
        assertNotNull(saved, "contact must be in the database");
        assertEquals(owner.getId(), saved.getUser().getId());
        assertTrue(body(session, "/user/show-contacts").contains("Rahul Patil"), "contact must appear immediately");
        assertTrue(body(session, "/user/" + saved.getCid() + "/contact").contains("9876543210"));
        assertTrue(body(session, "/user/index").contains("Rahul Patil"), "dashboard lists recent contacts");
    }

    @Test
    void invalidContactDataShowsErrorsBesideFieldsAndSavesNothing() throws Exception {
        String email = uniqueEmail();
        User owner = register(email);
        MockHttpSession session = login(email, PASSWORD);

        String html = mockMvc.perform(post("/user/process-contact").with(csrf()).session(session)
                .param("name", "").param("phone", "abc").param("email", "bad"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("Name is required"));
        assertTrue(html.contains("Enter a valid phone number"));
        assertTrue(html.contains("Enter a valid email address"));
        assertEquals(0, contacts.countByUserId(owner.getId()));
    }

    @Test
    void contactsCanBeEditedSearchedAndDeletedByTheirOwner() throws Exception {
        String email = uniqueEmail();
        User owner = register(email);
        MockHttpSession session = login(email, PASSWORD);
        addContact(session, "Rahul Patil", null);
        addContact(session, "Sneha Kulkarni", null);
        Contact rahul = contactOf(owner, "Rahul Patil");

        String edit = body(session, "/user/update-contact/" + rahul.getCid());
        assertTrue(edit.contains("Rahul Patil"), "edit form is prefilled");

        mockMvc.perform(post("/user/update-contact/" + rahul.getCid()).with(csrf()).session(session)
                .param("name", "Rahul P").param("phone", "9999999999"))
                .andExpect(status().is3xxRedirection());
        assertEquals("Rahul P", contacts.findById(rahul.getCid()).orElseThrow().getName());

        String search = mockMvc.perform(get("/user/show-contacts").param("q", "kulkarni").session(session))
                .andReturn().getResponse().getContentAsString();
        assertTrue(search.contains("Sneha Kulkarni") && !search.contains("Rahul P"), "search must filter");
        String none = mockMvc.perform(get("/user/show-contacts").param("q", "zzzz").session(session))
                .andReturn().getResponse().getContentAsString();
        assertTrue(none.contains("No matching contacts"));

        mockMvc.perform(post("/user/delete/" + rahul.getCid()).with(csrf()).session(session)).andExpect(status().is3xxRedirection());
        assertFalse(contacts.findById(rahul.getCid()).isPresent());
        assertNotNull(users.findByEmail(email), "deleting a contact must never delete the user");
        assertEquals(1, contacts.countByUserId(owner.getId()));
    }

    @Test
    void paginationWorksAcrossPages() throws Exception {
        String email = uniqueEmail();
        User owner = register(email);
        MockHttpSession session = login(email, PASSWORD);
        for (int i = 1; i <= 8; i++) {
            addContact(session, "Person " + (char) ('A' + i), null);
        }

        String first = body(session, "/user/show-contacts");
        String second = body(session, "/user/show-contacts/1");
        assertTrue(first.contains("Person B") && !first.contains("Person I"), "first page has the first six");
        assertTrue(second.contains("Person I") && !second.contains("Person B"), "second page has the rest");
        assertTrue(first.contains("page-link"), "pagination is shown for more than one page");
        mockMvc.perform(get("/user/show-contacts/9").session(session)).andExpect(status().is3xxRedirection());
        assertEquals(8, contacts.countByUserId(owner.getId()));
    }

    @Test
    void oneUserCannotReadChangeOrDeleteAnotherUsersContact() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        User a = register(emailA);
        register(emailB);
        MockHttpSession sessionA = login(emailA, PASSWORD);
        MockHttpSession sessionB = login(emailB, PASSWORD);
        addContact(sessionA, "Private Person", null);
        int cid = contactOf(a, "Private Person").getCid();

        assertEquals("/user/show-contacts", mockMvc.perform(get("/user/" + cid + "/contact").session(sessionB))
                .andReturn().getResponse().getRedirectedUrl());
        assertEquals("/user/show-contacts", mockMvc.perform(get("/user/update-contact/" + cid).session(sessionB))
                .andReturn().getResponse().getRedirectedUrl());
        mockMvc.perform(post("/user/update-contact/" + cid).with(csrf()).session(sessionB).param("name", "Hacked").param("phone", "9999999999"));
        mockMvc.perform(post("/user/delete/" + cid).with(csrf()).session(sessionB));

        Contact still = contacts.findById(cid).orElseThrow();
        assertEquals("Private Person", still.getName());
        assertEquals(a.getId(), still.getUser().getId());
        assertFalse(body(sessionB, "/user/show-contacts").contains("Private Person"));
    }

    @Test
    void searchEndpointOnlyReturnsOwnContactsAndNeverASecret() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        register(emailA);
        register(emailB);
        MockHttpSession sessionA = login(emailA, PASSWORD);
        MockHttpSession sessionB = login(emailB, PASSWORD);
        addContact(sessionA, "Findable Person", null);

        String mine = mockMvc.perform(get("/search/Findable").session(sessionA)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String theirs = mockMvc.perform(get("/search/Findable").session(sessionB)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(mine.contains("Findable Person"));
        assertEquals("[]", theirs);
        assertFalse(mine.contains("password") || mine.contains("$2"), "no password data in JSON");
        assertTrue(mockMvc.perform(get("/search/Findable")).andReturn().getResponse().getRedirectedUrl().endsWith("/signin"));
    }

    // ------------------------------------------------------------------ photos

    @Test
    void validPhotosAreStoredUnderSafeNamesAndServedOnlyToLoggedInUsers() throws Exception {
        String email = uniqueEmail();
        User owner = register(email);
        MockHttpSession session = login(email, PASSWORD);

        addContact(session, "Photo Person", new MockMultipartFile("profileImage", "../../evil.jsp", "image/png", png()));

        Contact saved = contactOf(owner, "Photo Person");
        assertNotNull(saved);
        assertTrue(saved.getImage().matches("[0-9a-f]{32}\\.png"), "safe generated name expected, was " + saved.getImage());

        mockMvc.perform(get("/user-photos/" + saved.getImage()).session(session))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.IMAGE_PNG));
        assertTrue(mockMvc.perform(get("/user-photos/" + saved.getImage())).andReturn().getResponse().getRedirectedUrl().endsWith("/signin"),
                "photos must not be public");
        assertTrue(body(session, "/user/show-contacts").contains("/user-photos/" + saved.getImage()));
    }

    @Test
    void invalidPhotosAreRejectedWithAMessageAndNothingIsSaved() throws Exception {
        String email = uniqueEmail();
        User owner = register(email);
        MockHttpSession session = login(email, PASSWORD);

        MvcResult result = addContact(session, "Bad Photo", new MockMultipartFile("profileImage", "x.jpg", "image/jpeg", "<?php evil(); ?>".getBytes()));

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("Only JPG, PNG, GIF or WebP"));
        assertNull(contactOf(owner, "Bad Photo"));
    }

    private static void assertNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNull(o);
    }

    // ------------------------------------------------------------------ profile and settings

    @Test
    void profileShowsTheLoggedInUserWithoutSecretsAndAllowsOnlyPermittedEdits() throws Exception {
        String email = uniqueEmail();
        User user = register(email);
        MockHttpSession session = login(email, PASSWORD);

        String html = body(session, "/user/profile");
        assertTrue(html.contains(email) && html.contains("Asha Patil"));
        assertFalse(html.contains(user.getPassword()) || html.contains("$2a$") || html.contains("$2b$"), "no password hash on the page");

        mockMvc.perform(post("/user/profile/edit").with(csrf()).session(session).param("name", "Asha Renamed")
                .param("about", "Hello there").param("email", "attacker@example.com").param("role", "ROLE_ADMIN"))
                .andExpect(status().is3xxRedirection());

        User after = users.findByEmail(email);
        assertEquals("Asha Renamed", after.getName());
        assertEquals("Hello there", after.getAbout());
        assertEquals(email, after.getEmail());
        assertEquals("ROLE_USER", after.getRole());
        assertEquals(user.getPassword(), after.getPassword());
        assertTrue(body(session, "/user/profile/edit").contains("Asha Renamed"));
    }

    @Test
    void changePasswordRejectsBadInputAndThenWorksForLogin() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);
        String hash = users.findByEmail(email).getPassword();

        assertTrue(body(session, "/user/setting").contains("Change password"));

        String wrong = changePassword(session, "WrongPassw0rd", "Newpass12", "Newpass12");
        assertTrue(wrong.contains("Current password is incorrect."));
        String mismatch = changePassword(session, PASSWORD, "Newpass12", "Different12");
        assertTrue(mismatch.contains("Passwords do not match"));
        String weak = changePassword(session, PASSWORD, "abcdefghij", "abcdefghij");
        assertTrue(weak.contains("Password must contain"));
        assertEquals(hash, users.findByEmail(email).getPassword(), "nothing may change after rejected attempts");

        mockMvc.perform(post("/user/change-password").with(csrf()).session(session).param("currentPassword", PASSWORD)
                .param("newPassword", "Newpass12").param("confirmPassword", "Newpass12"))
                .andExpect(status().is3xxRedirection());

        String stored = users.findByEmail(email).getPassword();
        assertTrue(stored.startsWith("$2") && !stored.contains("Newpass12"));
        assertEquals("/user/index", loginRedirect(email, "Newpass12"), "the new password must work");
        assertEquals("/signin?error", loginRedirect(email, PASSWORD), "the old password must stop working");
    }

    private String changePassword(MockHttpSession session, String current, String next, String confirm) throws Exception {
        return mockMvc.perform(post("/user/change-password").with(csrf()).session(session).param("currentPassword", current)
                .param("newPassword", next).param("confirmPassword", confirm))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void accountDeletionNeedsThePasswordAndKeepsDonationRecords() throws Exception {
        String email = uniqueEmail();
        User user = register(email);
        MockHttpSession session = login(email, PASSWORD);
        addContact(session, "Will Vanish", null);
        MyOrder order = new MyOrder();
        order.setOrderId("order_keep_" + UUID.randomUUID());
        order.setStatus("PAID");
        order.setUser(user);
        orders.save(order);

        mockMvc.perform(post("/user/account/delete").with(csrf()).session(session).param("currentPassword", "nope"))
                .andExpect(status().is3xxRedirection());
        assertNotNull(users.findByEmail(email), "wrong password must not delete anything");

        String location = mockMvc.perform(post("/user/account/delete").with(csrf()).session(session).param("currentPassword", PASSWORD))
                .andReturn().getResponse().getRedirectedUrl();
        assertEquals("/signin?deleted", location);
        assertEquals(null, users.findByEmail(email));
        assertEquals(0, contacts.countByUserId(user.getId()));
        MyOrder kept = orders.findByOrderId(order.getOrderId());
        assertNotNull(kept, "donation record must survive");
        assertEquals(null, kept.getUser());
    }

    // ------------------------------------------------------------------ forgot password

    @Test
    void forgotPasswordWorksEndToEndAndCannotBeBypassed() throws Exception {
        String email = uniqueEmail();
        register(email);
        when(emailService.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);

        // A direct reset without any verification must fail and change nothing.
        mockMvc.perform(post("/change-password").with(csrf()).param("newPassword", "Hacked123").param("confirmPassword", "Hacked123"))
                .andExpect(status().is3xxRedirection());
        assertEquals("/user/index", loginRedirect(email, PASSWORD));

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/send-otp").with(csrf()).session(session).param("email", email)).andExpect(status().is3xxRedirection());
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailService).sendEmail(anyString(), text.capture(), org.mockito.ArgumentMatchers.eq(email));
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(text.getValue());
        assertTrue(m.find());
        String code = m.group(1);

        mockMvc.perform(post("/verify-otp").with(csrf()).session(session).param("otp", code.equals("000000") ? "111111" : "000000"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/change-password").with(csrf()).session(session).param("newPassword", "Newpass12").param("confirmPassword", "Newpass12"));
        assertEquals("/user/index", loginRedirect(email, PASSWORD), "a wrong code must not unlock the reset");

        assertEquals("/change-password", mockMvc.perform(post("/verify-otp").with(csrf()).session(session).param("otp", code))
                .andReturn().getResponse().getRedirectedUrl());
        assertTrue(mockMvc.perform(get("/change-password").session(session)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString().contains("Choose a new password"));
        assertEquals("/signin", mockMvc.perform(post("/change-password").with(csrf()).session(session)
                .param("newPassword", "Newpass12").param("confirmPassword", "Newpass12")).andReturn().getResponse().getRedirectedUrl());

        assertEquals("/user/index", loginRedirect(email, "Newpass12"), "login with the new password");
        assertEquals("/signin?error", loginRedirect(email, PASSWORD), "the old password is rejected");
    }

    // ------------------------------------------------------------------ Razorpay (gateway mocked, no network, no money)

    private void stubGateway() {
        when(gateway.keyId()).thenReturn("rzp_test_dummykey");
        when(gateway.createOrder(anyLong(), anyString(), anyString())).thenAnswer(i ->
                new GatewayOrder("order_e2e_" + orderSeq.incrementAndGet() + "_" + UUID.randomUUID().toString().substring(0, 6), i.getArgument(0), i.getArgument(1)));
        when(gateway.verifySignature(anyString(), anyString(), anyString())).thenAnswer(i ->
                RazorpaySignature.isValid(i.getArgument(0), i.getArgument(1), i.getArgument(2), KEY_SECRET));
        when(gateway.verifyWebhookSignature(anyString(), anyString())).thenAnswer(i ->
                RazorpaySignature.isValidWebhook(i.getArgument(0), i.getArgument(1), WEBHOOK_SECRET));
    }

    private String createOrder(MockHttpSession session, int rupees) throws Exception {
        String json = mockMvc.perform(post("/user/create_order").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + rupees + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(rupees * 100)).andExpect(jsonPath("$.currency").value("INR"))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(json);
        assertTrue(m.find());
        return m.group(1);
    }

    private int verify(MockHttpSession session, String orderId, String paymentId, String signature) throws Exception {
        return mockMvc.perform(post("/user/update_order").with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"razorpay_order_id\":\"" + orderId + "\",\"razorpay_payment_id\":\"" + paymentId + "\",\"razorpay_signature\":\"" + signature + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void donationsRequireLoginAndValidateAmountsOnTheServer() throws Exception {
        stubGateway();
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);

        mockMvc.perform(post("/user/create_order").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().is3xxRedirection());
        for (String bad : List.of("{\"amount\":0}", "{\"amount\":-5}", "{\"amount\":100001}", "{\"amount\":1.234}", "{}", "nonsense")) {
            mockMvc.perform(post("/user/create_order").with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest());
        }
        String orderId = createOrder(session, 250);
        MyOrder stored = orders.findByOrderId(orderId);
        assertEquals("CREATED", stored.getStatus());
        assertEquals(25000L, stored.getAmountPaise());
        assertEquals(users.findByEmail(email).getId(), stored.getUser().getId());
        assertTrue(body(session, "/user/index").contains("Donate Now"), "the donation card is still on the dashboard");
    }

    @Test
    void aPaymentIsOnlyPaidAfterAValidSignatureAndOnlyForTheOwner() throws Exception {
        stubGateway();
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        register(emailA);
        register(emailB);
        MockHttpSession sessionA = login(emailA, PASSWORD);
        MockHttpSession sessionB = login(emailB, PASSWORD);
        String orderId = createOrder(sessionA, 500);
        String good = RazorpaySignature.compute(orderId, "pay_1", KEY_SECRET);

        assertEquals(400, verify(sessionA, orderId, "pay_1", "forged"));
        assertEquals("CREATED", orders.findByOrderId(orderId).getStatus());
        assertEquals(404, verify(sessionB, orderId, "pay_1", good), "another user cannot touch this order");
        assertEquals("CREATED", orders.findByOrderId(orderId).getStatus());

        assertEquals(200, verify(sessionA, orderId, "pay_1", good));
        MyOrder paid = orders.findByOrderId(orderId);
        assertEquals("PAID", paid.getStatus());
        assertEquals("pay_1", paid.getPaymentId());
        assertNotNull(paid.getPaidAt());

        assertEquals(200, verify(sessionA, orderId, "pay_1", good), "repeating the verification is harmless");
        assertEquals(409, verify(sessionA, orderId, "pay_2", RazorpaySignature.compute(orderId, "pay_2", KEY_SECRET)));
        assertEquals("pay_1", orders.findByOrderId(orderId).getPaymentId());
    }

    @Test
    void failedOrCancelledCheckoutsNeverBecomePaid() throws Exception {
        stubGateway();
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);
        String orderId = createOrder(session, 100);

        mockMvc.perform(post("/user/payment_failed").with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"razorpay_order_id\":\"" + orderId + "\",\"cancelled\":true,\"reason\":\"closed\"}")).andExpect(status().isOk());
        assertEquals("CANCELLED", orders.findByOrderId(orderId).getStatus());

        assertEquals(200, verify(session, orderId, "pay_9", RazorpaySignature.compute(orderId, "pay_9", KEY_SECRET)));
        mockMvc.perform(post("/user/payment_failed").with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"razorpay_order_id\":\"" + orderId + "\",\"cancelled\":false,\"reason\":\"forged\"}")).andExpect(status().isOk());
        assertEquals("PAID", orders.findByOrderId(orderId).getStatus(), "a failure report can never downgrade a paid order");
    }

    @Test
    void theWebhookReconcilesPaymentsOnlyWithAValidSignatureAndMatchingAmount() throws Exception {
        stubGateway();
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);
        String orderId = createOrder(session, 250);

        String wrongAmount = webhookBody(orderId, "pay_w1", 100);
        String correct = webhookBody(orderId, "pay_w1", 25000);

        mockMvc.perform(post("/razorpay/webhook").contentType(MediaType.APPLICATION_JSON).content(correct))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/razorpay/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", RazorpaySignature.hmacHex(correct, "wrong-secret")).content(correct))
                .andExpect(status().isUnauthorized());
        assertEquals("CREATED", orders.findByOrderId(orderId).getStatus());

        mockMvc.perform(post("/razorpay/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", RazorpaySignature.hmacHex(wrongAmount, WEBHOOK_SECRET)).content(wrongAmount))
                .andExpect(status().isOk());
        assertEquals("CREATED", orders.findByOrderId(orderId).getStatus(), "amount mismatch must not mark paid");

        mockMvc.perform(post("/razorpay/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", RazorpaySignature.hmacHex(correct, WEBHOOK_SECRET)).content(correct))
                .andExpect(status().isOk());
        MyOrder paid = orders.findByOrderId(orderId);
        assertEquals("PAID", paid.getStatus());
        assertEquals("pay_w1", paid.getPaymentId());
    }

    private static String webhookBody(String orderId, String paymentId, long amountPaise) {
        return "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"" + paymentId
                + "\",\"order_id\":\"" + orderId + "\",\"amount\":" + amountPaise + ",\"currency\":\"INR\"}}}}";
    }
}
