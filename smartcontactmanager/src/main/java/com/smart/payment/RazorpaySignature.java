package com.smart.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Razorpay payment signature check, exactly as documented by Razorpay:
 * {@code hex(HMAC_SHA256(orderId + "|" + paymentId, keySecret))} must equal the
 * {@code razorpay_signature} returned by Checkout.
 * The comparison is constant-time so it does not leak how much of a forged signature matched.
 */
public final class RazorpaySignature {

    private static final String ALGORITHM = "HmacSHA256";

    private RazorpaySignature() {
    }

    public static String compute(String orderId, String paymentId, String secret) {
        return hmacHex(orderId + "|" + paymentId, secret);
    }

    /** Lower-case hex HMAC-SHA256 of {@code payload}. */
    public static String hmacHex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    /** Webhook check: {@code X-Razorpay-Signature} must equal hex(HMAC_SHA256(rawBody, webhookSecret)). */
    public static boolean isValidWebhook(String rawBody, String signature, String webhookSecret) {
        if (isBlank(rawBody) || isBlank(signature) || isBlank(webhookSecret)) {
            return false;
        }
        return MessageDigest.isEqual(hmacHex(rawBody, webhookSecret).getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isValid(String orderId, String paymentId, String signature, String secret) {
        if (isBlank(orderId) || isBlank(paymentId) || isBlank(signature) || isBlank(secret)) {
            return false;
        }
        byte[] expected = compute(orderId, paymentId, secret).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
