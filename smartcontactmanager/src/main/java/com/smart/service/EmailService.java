package com.smart.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Sends a plain-text email. Returns false (instead of throwing) when delivery fails.
     * The message body (which may contain a one-time code) and the address are never logged.
     */
    public boolean sendEmail(String subject, String message, String to) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(to);
            mail.setSubject(subject);
            mail.setText(message);
            mailSender.send(mail);
            return true;
        } catch (Exception e) {
            log.error("Email delivery failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
