package com.smart.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.smart.payment.PaymentDtos.ApiMessage;
import com.smart.payment.PaymentException;
import com.smart.payment.RazorpayWebhookHandler;

/**
 * Receives Razorpay webhooks. This one URL is excluded from CSRF (Razorpay cannot send a token) and is
 * instead protected by the HMAC signature of the raw body, which is checked before anything is read.
 */
@RestController
public class WebhookController {

    private final RazorpayWebhookHandler handler;

    public WebhookController(RazorpayWebhookHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/razorpay/webhook")
    public ResponseEntity<ApiMessage> receive(@RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        try {
            handler.handle(rawBody, signature);
            return ResponseEntity.ok(new ApiMessage("ok", "Received."));
        } catch (PaymentException e) {
            return ResponseEntity.status(e.getStatus()).body(new ApiMessage("error", e.getMessage()));
        }
    }
}
