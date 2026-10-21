package com.smart.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smart.Dao.UserRepository;
import com.smart.entity.User;
import com.smart.helper.Message;
import com.smart.service.EmailService;
import com.smart.service.PasswordPolicy;

import jakarta.servlet.http.HttpSession;

/**
 * Forgot-password flow: email -> OTP -> new password.
 * <ul>
 * <li>The OTP comes from {@link SecureRandom}, expires after 5 minutes, allows 5 wrong attempts and can be
 * used once. Requesting a new code replaces (invalidates) the previous one; resends are rate limited.</li>
 * <li>After a correct OTP the session receives a short-lived (10 minutes), single-use reset authorization.</li>
 * <li>The new password can only be set after the OTP was verified in the same session
 * (previously /change-password did not check this).</li>
 * <li>The response never reveals whether an email address is registered.</li>
 * </ul>
 */
@Controller
public class ForgetController {

    private static final Logger log = LoggerFactory.getLogger(ForgetController.class);

    static final String SESSION_EMAIL = "reset.email";
    static final String SESSION_OTP = "reset.otp";
    static final String SESSION_EXPIRES = "reset.expires";
    static final String SESSION_ATTEMPTS = "reset.attempts";
    static final String SESSION_VERIFIED = "reset.verifiedUntil";
    static final String SESSION_SEND_COUNT = "reset.sendCount";
    static final String SESSION_LAST_SENT = "reset.lastSent";

    private static final long OTP_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long RESET_AUTH_TTL_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_SENDS_PER_SESSION = 5;
    private static final long RESEND_COOLDOWN_MILLIS = 60 * 1000L;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordPolicy passwordPolicy;

    @GetMapping("/forgot")
    public String openEmailForm() {
        return "forgot_email_form";
    }

    @PostMapping("/send-otp")
    public String sendOTP(@RequestParam("email") String email, RedirectAttributes redirectAttributes,
            HttpSession session) {
        String normalized = email == null ? "" : email.trim();

        Long lastSent = (Long) session.getAttribute(SESSION_LAST_SENT);
        if (lastSent != null && System.currentTimeMillis() - lastSent < RESEND_COOLDOWN_MILLIS) {
            redirectAttributes.addFlashAttribute("message",
                    new Message("Please wait a minute before requesting another code.", "warning"));
            return "redirect:/forgot";
        }

        int sends = session.getAttribute(SESSION_SEND_COUNT) instanceof Integer n ? n : 0;
        if (sends >= MAX_SENDS_PER_SESSION) {
            redirectAttributes.addFlashAttribute("message",
                    new Message("Too many requests. Please try again later.", "danger"));
            return "redirect:/forgot";
        }
        session.setAttribute(SESSION_SEND_COUNT, sends + 1);

        clearResetState(session);
        session.setAttribute(SESSION_EMAIL, normalized);
        session.setAttribute(SESSION_LAST_SENT, System.currentTimeMillis());
        session.setAttribute(SESSION_EXPIRES, System.currentTimeMillis() + OTP_TTL_MILLIS);
        session.setAttribute(SESSION_ATTEMPTS, 0);

        User user = userRepository.findByEmail(normalized);
        if (user != null) {
            String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
            boolean sent = emailService.sendEmail("Password Reset OTP",
                    "Your OTP is: " + otp + "\nIt expires in 5 minutes. If you did not ask for it, ignore this email.",
                    normalized);
            if (!sent) {
                clearResetState(session);
                redirectAttributes.addFlashAttribute("message",
                        new Message("We could not send the email. Please try again later.", "danger"));
                return "redirect:/forgot";
            }
            session.setAttribute(SESSION_OTP, otp);
            log.info("Password reset code sent to user {}", user.getId());
        }
        // Same answer whether or not the address is registered.
        redirectAttributes.addFlashAttribute("message",
                new Message("If that address is registered, a 6-digit code has been sent to it.", "success"));
        return "redirect:/verify-otp";
    }

    @GetMapping("/verify-otp")
    public String openVerifyOtpPage(HttpSession session) {
        return session.getAttribute(SESSION_EMAIL) == null ? "redirect:/forgot" : "verify_otp";
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam("otp") String otp, HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (session.getAttribute(SESSION_EMAIL) == null) {
            return "redirect:/forgot";
        }
        Long expires = (Long) session.getAttribute(SESSION_EXPIRES);
        if (expires == null || System.currentTimeMillis() > expires) {
            clearResetState(session);
            redirectAttributes.addFlashAttribute("message",
                    new Message("That code has expired. Please request a new one.", "danger"));
            return "redirect:/forgot";
        }
        int attempts = session.getAttribute(SESSION_ATTEMPTS) instanceof Integer n ? n + 1 : 1;
        session.setAttribute(SESSION_ATTEMPTS, attempts);
        if (attempts > MAX_ATTEMPTS) {
            clearResetState(session);
            redirectAttributes.addFlashAttribute("message",
                    new Message("Too many wrong attempts. Please request a new code.", "danger"));
            return "redirect:/forgot";
        }

        String expected = (String) session.getAttribute(SESSION_OTP);
        if (expected != null && otp != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), otp.trim().getBytes(StandardCharsets.UTF_8))) {
            session.setAttribute(SESSION_VERIFIED, System.currentTimeMillis() + RESET_AUTH_TTL_MILLIS);
            session.removeAttribute(SESSION_OTP); // a code can be used only once
            return "redirect:/change-password";
        }
        redirectAttributes.addFlashAttribute("message", new Message("Invalid code. Please try again.", "danger"));
        return "redirect:/verify-otp";
    }

    @GetMapping("/change-password")
    public String openChangePasswordPage(HttpSession session, Model model) {
        if (!isVerified(session)) {
            return "redirect:/forgot";
        }
        model.addAttribute("passwordHint", passwordPolicy.describe());
        return "change_password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isVerified(session)) {
            redirectAttributes.addFlashAttribute("message",
                    new Message("Please verify the code sent to your email first.", "danger"));
            return "redirect:/forgot";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("message", new Message("Passwords do not match.", "danger"));
            return "redirect:/change-password";
        }
        var problem = passwordPolicy.check(newPassword);
        if (problem.isPresent()) {
            redirectAttributes.addFlashAttribute("message", new Message(problem.get(), "danger"));
            return "redirect:/change-password";
        }
        User user = userRepository.findByEmail((String) session.getAttribute(SESSION_EMAIL));
        if (user == null) {
            clearResetState(session);
            redirectAttributes.addFlashAttribute("message", new Message("Something went wrong. Please start again.", "danger"));
            return "redirect:/forgot";
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        clearResetState(session);
        log.info("Password reset completed for user {}", user.getId());
        redirectAttributes.addFlashAttribute("message",
                new Message("Password changed successfully. Please log in.", "success"));
        return "redirect:/signin";
    }

    private static boolean isVerified(HttpSession session) {
        return session.getAttribute(SESSION_VERIFIED) instanceof Long until
                && System.currentTimeMillis() <= until
                && session.getAttribute(SESSION_EMAIL) != null;
    }

    private static void clearResetState(HttpSession session) {
        session.removeAttribute(SESSION_EMAIL);
        session.removeAttribute(SESSION_OTP);
        session.removeAttribute(SESSION_EXPIRES);
        session.removeAttribute(SESSION_ATTEMPTS);
        session.removeAttribute(SESSION_VERIFIED);
    }
}
