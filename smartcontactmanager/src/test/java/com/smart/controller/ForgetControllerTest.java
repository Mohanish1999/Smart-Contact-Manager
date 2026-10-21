package com.smart.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.smart.Dao.UserRepository;
import com.smart.entity.User;
import com.smart.helper.Message;
import com.smart.service.EmailService;
import com.smart.service.PasswordPolicy;

/** The complete recovery flow over HTTP: email -> OTP -> verify -> new password. Email delivery is mocked. */
@WebMvcTest(ForgetController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PasswordPolicy.class)
class ForgetControllerTest {

    private static final String EMAIL = "asha@example.com";

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private EmailService emailService;
    @MockBean
    private PasswordEncoder passwordEncoder;

    private User asha;

    @BeforeEach
    void setUp() {
        asha = new User();
        asha.setEmail(EMAIL);
        asha.setPassword("OLD-HASH");
        when(userRepository.findByEmail(EMAIL)).thenReturn(asha);
        when(emailService.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenAnswer(i -> "ENC:" + i.getArgument(0));
    }

    private String requestCode(MockHttpSession session) throws Exception {
        mockMvc.perform(post("/send-otp").session(session).param("email", EMAIL))
                .andExpect(redirectedUrl("/verify-otp"));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendEmail(anyString(), body.capture(), eq(EMAIL));
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(body.getValue());
        assertTrue(m.find(), "the email must contain a 6-digit code");
        return m.group(1);
    }

    private static String otherThan(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    // ------------------------------------------------------------ sending

    @Test
    void forgotPageOpens() throws Exception {
        mockMvc.perform(get("/forgot")).andExpect(status().isOk()).andExpect(view().name("forgot_email_form"));
    }

    @Test
    void aRegisteredEmailReceivesASixDigitCode() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String code = requestCode(session);
        assertEquals(6, code.length());
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAndNoEmailIsSent() throws Exception {
        MockHttpSession known = new MockHttpSession();
        MockHttpSession unknown = new MockHttpSession();

        var knownResult = mockMvc.perform(post("/send-otp").session(known).param("email", EMAIL)).andReturn();
        var unknownResult = mockMvc.perform(post("/send-otp").session(unknown).param("email", "nobody@example.com")).andReturn();

        assertEquals(knownResult.getResponse().getRedirectedUrl(), unknownResult.getResponse().getRedirectedUrl());
        assertEquals(((Message) knownResult.getFlashMap().get("message")).getContent(),
                ((Message) unknownResult.getFlashMap().get("message")).getContent());
        verify(emailService, times(1)).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void anUnknownEmailCanNeverBeVerified() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/send-otp").session(session).param("email", "nobody@example.com"));

        mockMvc.perform(post("/verify-otp").session(session).param("otp", "123456")).andExpect(redirectedUrl("/verify-otp"));
        mockMvc.perform(post("/change-password").session(session).param("newPassword", "Newpass12").param("confirmPassword", "Newpass12"))
                .andExpect(redirectedUrl("/forgot"));
    }

    @Test
    void anEmailFailureIsReportedAndNothingIsKept() throws Exception {
        when(emailService.sendEmail(anyString(), anyString(), anyString())).thenReturn(false);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/send-otp").session(session).param("email", EMAIL))
                .andExpect(redirectedUrl("/forgot"))
                .andExpect(flash().attributeExists("message"));
        assertNull(session.getAttribute(ForgetController.SESSION_OTP));
    }

    @Test
    void anotherRequestInsideTheCooldownIsRefused() throws Exception {
        MockHttpSession session = new MockHttpSession();
        requestCode(session);

        mockMvc.perform(post("/send-otp").session(session).param("email", EMAIL)).andExpect(redirectedUrl("/forgot"));

        verify(emailService, times(1)).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void aNewCodeInvalidatesThePreviousOne() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String first = requestCode(session);
        session.setAttribute(ForgetController.SESSION_LAST_SENT, System.currentTimeMillis() - 120_000);
        String second = requestCode(session);

        if (!first.equals(second)) {
            mockMvc.perform(post("/verify-otp").session(session).param("otp", first)).andExpect(redirectedUrl("/verify-otp"));
        }
        mockMvc.perform(post("/verify-otp").session(session).param("otp", second)).andExpect(redirectedUrl("/change-password"));
    }

    @Test
    void atMostFiveCodesAreSentPerSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < 8; i++) {
            session.setAttribute(ForgetController.SESSION_LAST_SENT, System.currentTimeMillis() - 120_000);
            mockMvc.perform(post("/send-otp").session(session).param("email", EMAIL));
        }
        verify(emailService, times(5)).sendEmail(anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------ verifying

    @Test
    void theCorrectCodeUnlocksThePasswordPage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String code = requestCode(session);

        mockMvc.perform(post("/verify-otp").session(session).param("otp", code)).andExpect(redirectedUrl("/change-password"));
        mockMvc.perform(get("/change-password").session(session)).andExpect(status().isOk()).andExpect(view().name("change_password"));
    }

    @Test
    void aWrongCodeIsRejectedAndDoesNotUnlockAnything() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String code = requestCode(session);

        mockMvc.perform(post("/verify-otp").session(session).param("otp", otherThan(code)))
                .andExpect(redirectedUrl("/verify-otp"))
                .andExpect(flash().attributeExists("message"));
        mockMvc.perform(get("/change-password").session(session)).andExpect(redirectedUrl("/forgot"));
    }

    @Test
    void aUsedCodeCannotBeReused() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String code = requestCode(session);
        mockMvc.perform(post("/verify-otp").session(session).param("otp", code)).andExpect(redirectedUrl("/change-password"));

        mockMvc.perform(post("/verify-otp").session(session).param("otp", code)).andExpect(redirectedUrl("/verify-otp"));
        assertNull(session.getAttribute(ForgetController.SESSION_OTP));
    }

    @Test
    void anExpiredCodeIsRejectedEvenIfCorrect() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String code = requestCode(session);
        session.setAttribute(ForgetController.SESSION_EXPIRES, System.currentTimeMillis() - 1000);

        mockMvc.perform(post("/verify-otp").session(session).param("otp", code))
                .andExpect(redirectedUrl("/forgot"))
                .andExpect(flash().attributeExists("message"));
        assertNull(session.getAttribute(ForgetController.SESSION_EMAIL));
    }

    @Test
    void theCodeLivesForFiveMinutes() throws Exception {
        MockHttpSession session = new MockHttpSession();
        long before = System.currentTimeMillis();
        requestCode(session);

        long lifetime = (Long) session.getAttribute(ForgetController.SESSION_EXPIRES) - before;
        assertTrue(lifetime >= 299_000 && lifetime <= 302_000, "lifetime was " + lifetime);
    }

    @Test
    void tooManyWrongAttemptsLockTheRequestEvenForTheCorrectCode() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String code = requestCode(session);
        String wrong = otherThan(code);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/verify-otp").session(session).param("otp", wrong)).andExpect(redirectedUrl("/verify-otp"));
        }

        mockMvc.perform(post("/verify-otp").session(session).param("otp", wrong)).andExpect(redirectedUrl("/forgot"));
        mockMvc.perform(post("/verify-otp").session(session).param("otp", code)).andExpect(redirectedUrl("/forgot"));
    }

    @Test
    void theVerifyPageNeedsAPendingRequest() throws Exception {
        mockMvc.perform(get("/verify-otp").session(new MockHttpSession())).andExpect(redirectedUrl("/forgot"));
    }

    // ------------------------------------------------------------ resetting

    @Test
    void aDirectResetWithoutVerificationIsRefused() throws Exception {
        mockMvc.perform(post("/change-password").session(new MockHttpSession())
                .param("newPassword", "Hacked123").param("confirmPassword", "Hacked123"))
                .andExpect(redirectedUrl("/forgot"));

        verify(userRepository, never()).save(any(User.class));
        assertEquals("OLD-HASH", asha.getPassword());
    }

    @Test
    void aResetAfterAWrongCodeIsRefused() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String code = requestCode(session);
        mockMvc.perform(post("/verify-otp").session(session).param("otp", otherThan(code)));

        mockMvc.perform(post("/change-password").session(session).param("newPassword", "Newpass12").param("confirmPassword", "Newpass12"))
                .andExpect(redirectedUrl("/forgot"));
        assertEquals("OLD-HASH", asha.getPassword());
    }

    @Test
    void aStaleResetAuthorizationIsRefused() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/verify-otp").session(session).param("otp", requestCode(session)));
        session.setAttribute(ForgetController.SESSION_VERIFIED, System.currentTimeMillis() - 1);

        mockMvc.perform(post("/change-password").session(session).param("newPassword", "Newpass12").param("confirmPassword", "Newpass12"))
                .andExpect(redirectedUrl("/forgot"));
        assertEquals("OLD-HASH", asha.getPassword());
    }

    @Test
    void aConfirmationMismatchIsRejected() throws Exception {
        MockHttpSession session = verifiedSession();

        mockMvc.perform(post("/change-password").session(session).param("newPassword", "Newpass12").param("confirmPassword", "Different1"))
                .andExpect(redirectedUrl("/change-password"))
                .andExpect(flash().attributeExists("message"));
        assertEquals("OLD-HASH", asha.getPassword());
    }

    @Test
    void aPasswordBreakingThePolicyIsRejected() throws Exception {
        MockHttpSession session = verifiedSession();

        mockMvc.perform(post("/change-password").session(session).param("newPassword", "short").param("confirmPassword", "short"))
                .andExpect(redirectedUrl("/change-password"));
        assertEquals("OLD-HASH", asha.getPassword());
    }

    @Test
    void aVerifiedResetStoresTheHashClearsTheStateAndSendsTheUserToLogin() throws Exception {
        MockHttpSession session = verifiedSession();

        mockMvc.perform(post("/change-password").session(session).param("newPassword", "Newpass12").param("confirmPassword", "Newpass12"))
                .andExpect(redirectedUrl("/signin"))
                .andExpect(flash().attributeExists("message"));

        assertEquals("ENC:Newpass12", asha.getPassword());
        assertNotEquals("Newpass12", asha.getPassword());
        verify(userRepository).save(asha);
        assertNull(session.getAttribute(ForgetController.SESSION_EMAIL));
        assertFalse(session.getAttribute(ForgetController.SESSION_VERIFIED) != null);
    }

    @Test
    void theResetAuthorizationIsSingleUse() throws Exception {
        MockHttpSession session = verifiedSession();
        mockMvc.perform(post("/change-password").session(session).param("newPassword", "Newpass12").param("confirmPassword", "Newpass12"));

        mockMvc.perform(post("/change-password").session(session).param("newPassword", "Another123").param("confirmPassword", "Another123"))
                .andExpect(redirectedUrl("/forgot"));

        verify(userRepository, times(1)).save(any(User.class));
        assertEquals("ENC:Newpass12", asha.getPassword());
    }

    private MockHttpSession verifiedSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/verify-otp").session(session).param("otp", requestCode(session)))
                .andExpect(redirectedUrl("/change-password"));
        return session;
    }
}
