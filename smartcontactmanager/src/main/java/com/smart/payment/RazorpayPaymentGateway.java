package com.smart.payment;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

/** Razorpay implementation of {@link PaymentGateway}, using the official Razorpay Java SDK. */
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);

    private final RazorpayProperties properties;
    private volatile RazorpayClient client;

    public RazorpayPaymentGateway(RazorpayProperties properties) {
        this.properties = properties;
    }

    @Override
    public GatewayOrder createOrder(long amountPaise, String currency, String receipt) {
        requireConfigured();
        try {
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            request.put("currency", currency);
            request.put("receipt", receipt);

            Order order = client().orders.create(request);

            String id = order.get("id");
            Number amount = order.get("amount");
            String returnedCurrency = order.get("currency");

            // Defence in depth: the order Razorpay created must be the one we asked for.
            if (id == null || amount == null || amount.longValue() != amountPaise) {
                log.error("Razorpay returned an unexpected order for receipt {}", receipt);
                throw new PaymentException(HttpStatus.BAD_GATEWAY,
                        "Could not create the payment order. Please try again shortly.");
            }
            return new GatewayOrder(id, amount.longValue(), returnedCurrency);
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new PaymentException(HttpStatus.BAD_GATEWAY,
                    "Could not create the payment order. Please try again shortly.");
        }
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        requireConfigured();
        return RazorpaySignature.isValid(orderId, paymentId, signature, properties.keySecret());
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return RazorpaySignature.isValidWebhook(rawBody, signature, properties.webhookSecret());
    }

    @Override
    public String keyId() {
        return properties.keyId();
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            log.error("Razorpay is not configured: set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Donations are temporarily unavailable.");
        }
    }

    private RazorpayClient client() throws RazorpayException {
        RazorpayClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = new RazorpayClient(properties.keyId(), properties.keySecret());
                    client = local;
                }
            }
        }
        return local;
    }
}
