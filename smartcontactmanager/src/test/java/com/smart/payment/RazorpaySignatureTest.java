package com.smart.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RazorpaySignatureTest {

    private static final String SECRET = "my_test_secret";
    private static final String ORDER = "order_TEST123";
    private static final String PAYMENT = "pay_TEST456";

    /** Independently produced with: printf 'order_TEST123|pay_TEST456' | openssl dgst -sha256 -hmac my_test_secret */
    private static final String KNOWN_SIGNATURE = "7fc44fda70774c34b72a1931f041c1b6c3691b35a36c9035812e7ab94b956f85";

    @Test
    void computesTheDocumentedHmacSha256() {
        assertEquals(KNOWN_SIGNATURE, RazorpaySignature.compute(ORDER, PAYMENT, SECRET));
    }

    @Test
    void acceptsValidSignature() {
        assertTrue(RazorpaySignature.isValid(ORDER, PAYMENT, KNOWN_SIGNATURE, SECRET));
    }

    @Test
    void rejectsSignatureForDifferentOrderOrPayment() {
        assertFalse(RazorpaySignature.isValid("order_OTHER", PAYMENT, KNOWN_SIGNATURE, SECRET));
        assertFalse(RazorpaySignature.isValid(ORDER, "pay_OTHER", KNOWN_SIGNATURE, SECRET));
    }

    @Test
    void rejectsSignatureMadeWithAnotherSecret() {
        assertFalse(RazorpaySignature.isValid(ORDER, PAYMENT, KNOWN_SIGNATURE, "another_secret"));
    }

    @Test
    void rejectsTamperedOrMalformedSignature() {
        String tampered = KNOWN_SIGNATURE.substring(0, KNOWN_SIGNATURE.length() - 1) + "0";
        assertFalse(RazorpaySignature.isValid(ORDER, PAYMENT, tampered, SECRET));
        assertFalse(RazorpaySignature.isValid(ORDER, PAYMENT, "deadbeef", SECRET));
        assertFalse(RazorpaySignature.isValid(ORDER, PAYMENT, KNOWN_SIGNATURE + "00", SECRET));
    }

    @Test
    void rejectsMissingValues() {
        assertFalse(RazorpaySignature.isValid(null, PAYMENT, KNOWN_SIGNATURE, SECRET));
        assertFalse(RazorpaySignature.isValid(ORDER, "", KNOWN_SIGNATURE, SECRET));
        assertFalse(RazorpaySignature.isValid(ORDER, PAYMENT, " ", SECRET));
        assertFalse(RazorpaySignature.isValid(ORDER, PAYMENT, KNOWN_SIGNATURE, null));
    }

    @Test
    void webhookSignatureIsHmacOfTheRawBody() {
        String body = "{\"event\":\"payment.captured\"}";
        String signature = RazorpaySignature.hmacHex(body, "whsec");

        assertTrue(RazorpaySignature.isValidWebhook(body, signature, "whsec"));
        assertFalse(RazorpaySignature.isValidWebhook(body + "x", signature, "whsec"));
        assertFalse(RazorpaySignature.isValidWebhook(body, signature, "other"));
        assertFalse(RazorpaySignature.isValidWebhook(body, "", "whsec"));
        assertFalse(RazorpaySignature.isValidWebhook(body, signature, ""));
    }
}
