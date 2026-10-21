package com.smart.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies and applies Razorpay webhook calls. Only {@code payment.captured} and {@code order.paid}
 * change anything; every other event is acknowledged and ignored.
 */
@Service
public class RazorpayWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookHandler.class);

    private final PaymentGateway gateway;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public RazorpayWebhookHandler(PaymentGateway gateway, PaymentService paymentService, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    /** @throws PaymentException 401 when the signature is missing or wrong, 400 when the body is not understood */
    public void handle(String rawBody, String signature) {
        if (!gateway.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Rejected a webhook with an invalid or missing signature");
            throw new PaymentException(HttpStatus.UNAUTHORIZED, "Invalid signature.");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText("");
            if (!"payment.captured".equals(event) && !"order.paid".equals(event)) {
                return;
            }
            JsonNode payment = root.path("payload").path("payment").path("entity");
            paymentService.markPaidFromWebhook(payment.path("order_id").asText(null), payment.path("id").asText(null),
                    payment.path("amount").asLong(-1), payment.path("currency").asText(null));
        } catch (java.io.IOException e) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Unreadable webhook body.");
        }
    }
}
