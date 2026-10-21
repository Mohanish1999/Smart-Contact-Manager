package com.smart.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** No network: only configuration handling and signature verification are exercised here. */
class RazorpayPaymentGatewayTest {

    private static final String SECRET = "my_test_secret";

    private RazorpayPaymentGateway gateway(String keyId, String secret) {
        return new RazorpayPaymentGateway(new RazorpayProperties(keyId, secret, "INR", 1, 100000, "SCM", "whsec_test"));
    }

    @Test
    void verifiesSignaturesWithTheConfiguredSecret() {
        RazorpayPaymentGateway gateway = gateway("rzp_test_abc", SECRET);
        String signature = RazorpaySignature.compute("order_1", "pay_1", SECRET);

        assertTrue(gateway.verifySignature("order_1", "pay_1", signature));
        assertFalse(gateway.verifySignature("order_1", "pay_2", signature));
        assertFalse(gateway.verifySignature("order_1", "pay_1", "forged"));
    }

    @Test
    void exposesOnlyThePublicKeyId() {
        assertEquals("rzp_test_abc", gateway("rzp_test_abc", SECRET).keyId());
    }

    @Test
    void refusesToWorkWithoutCredentials() {
        RazorpayPaymentGateway gateway = gateway("", "");

        PaymentException order = assertThrows(PaymentException.class, () -> gateway.createOrder(10000, "INR", "rcpt"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, order.getStatus());

        PaymentException verify = assertThrows(PaymentException.class, () -> gateway.verifySignature("o", "p", "s"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, verify.getStatus());
    }

    @Test
    void propertiesNeverPrintTheSecret() {
        RazorpayProperties props = new RazorpayProperties("rzp_test_abc", "TOP_SECRET_VALUE", "INR", 1, 100000, "SCM", "TOP_WEBHOOK_SECRET");
        assertFalse(props.toString().contains("TOP_SECRET_VALUE"));
        assertFalse(props.toString().contains("TOP_WEBHOOK_SECRET"));
    }

    @Test
    void verifiesWebhookSignaturesWithTheWebhookSecret() {
        RazorpayPaymentGateway gateway = gateway("rzp_test_abc", SECRET);
        String body = "{\"event\":\"payment.captured\"}";
        String signature = RazorpaySignature.hmacHex(body, "whsec_test");

        assertTrue(gateway.verifyWebhookSignature(body, signature));
        assertFalse(gateway.verifyWebhookSignature(body + " ", signature));
        assertFalse(gateway.verifyWebhookSignature(body, RazorpaySignature.hmacHex(body, SECRET)));
        assertFalse(gateway.verifyWebhookSignature(body, null));
    }

    @Test
    void webhooksAreRejectedWhenNoWebhookSecretIsConfigured() {
        RazorpayPaymentGateway gateway = new RazorpayPaymentGateway(
                new RazorpayProperties("rzp_test_abc", SECRET, "INR", 1, 100000, "SCM", ""));
        assertFalse(gateway.verifyWebhookSignature("{}", RazorpaySignature.hmacHex("{}", "")));
    }
}
