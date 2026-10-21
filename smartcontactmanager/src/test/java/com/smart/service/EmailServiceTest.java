package com.smart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Test
    void testSendEmailSuccess() {

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        boolean result = emailService.sendEmail(
                "Test Subject",
                "Test Message",
                "test@gmail.com"
        );

        assertTrue(result);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
    @Test
    void testSendEmailFailure() {
    	try {
        doThrow(new RuntimeException("Email sending failed"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));
    	}catch (Exception e) {
    	    System.out.println("Email Error : " + e.getMessage());
    	    e.printStackTrace();
    	}
        boolean result = emailService.sendEmail(
                "Test Subject",
                "Test Message",
                "test@gmail.com"
        );

        assertFalse(result);

        verify(mailSender, times(1))
                .send(any(SimpleMailMessage.class));
    }
}