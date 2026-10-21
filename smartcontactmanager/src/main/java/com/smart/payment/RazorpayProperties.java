package com.smart.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Razorpay settings, bound from the {@code razorpay.*} properties.
 * The key id/secret are supplied through the RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET
 * environment variables (see application.properties and RAZORPAY_SETUP.md).
 *
 * @param minAmount smallest accepted donation, in whole currency units (e.g. rupees)
 * @param maxAmount largest accepted donation, in whole currency units
 * @param webhookSecret secret configured for the Razorpay webhook (RAZORPAY_WEBHOOK_SECRET); optional
 */
@ConfigurationProperties(prefix = "razorpay")
public record RazorpayProperties(
        String keyId,
        String keySecret,
        @DefaultValue("INR") String currency,
        @DefaultValue("1") long minAmount,
        @DefaultValue("100000") long maxAmount,
        @DefaultValue("Smart Contact Manager") String merchantName,
        String webhookSecret) {

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    /** Never print the secret, even by accident (logs, debuggers). */
    @Override
    public String toString() {
        return "RazorpayProperties[keyId=" + keyId + ", keySecret=****, webhookSecret=****, currency=" + currency + "]";
    }
}
