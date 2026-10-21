package com.smart.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RazorpayWebhookHandlerTest {

    private static final String BODY = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
            + "{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":25000,\"currency\":\"INR\"}}}}";

    @Mock
    private PaymentGateway gateway;
    @Mock
    private PaymentService paymentService;

    private RazorpayWebhookHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RazorpayWebhookHandler(gateway, paymentService, new ObjectMapper());
    }

    @Test
    void validCapturedEventMarksTheOrderPaid() {
        when(gateway.verifyWebhookSignature(BODY, "sig")).thenReturn(true);

        handler.handle(BODY, "sig");

        verify(paymentService).markPaidFromWebhook("order_1", "pay_1", 25000, "INR");
    }

    @Test
    void invalidSignatureIsRejectedBeforeAnythingIsRead() {
        when(gateway.verifyWebhookSignature(BODY, "forged")).thenReturn(false);

        PaymentException ex = assertThrows(PaymentException.class, () -> handler.handle(BODY, "forged"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(paymentService, never()).markPaidFromWebhook(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void missingSignatureIsRejected() {
        PaymentException ex = assertThrows(PaymentException.class, () -> handler.handle(BODY, null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void otherEventsAreAcknowledgedAndIgnored() {
        String body = "{\"event\":\"payment.failed\",\"payload\":{}}";
        when(gateway.verifyWebhookSignature(body, "sig")).thenReturn(true);

        handler.handle(body, "sig");

        verify(paymentService, never()).markPaidFromWebhook(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void unreadableBodyIsABadRequest() {
        when(gateway.verifyWebhookSignature("not json", "sig")).thenReturn(true);

        PaymentException ex = assertThrows(PaymentException.class, () -> handler.handle("not json", "sig"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
