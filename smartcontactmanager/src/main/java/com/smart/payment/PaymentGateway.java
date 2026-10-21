package com.smart.payment;

/**
 * The only door to the payment provider. {@link PaymentService} depends on this interface,
 * so the business rules can be tested with a mock and no network or real money.
 */
public interface PaymentGateway {

    /** Creates an order on the provider's side. The amount is in paise. */
    GatewayOrder createOrder(long amountPaise, String currency, String receipt);

    /** True only if the signature is the valid HMAC-SHA256 of {@code orderId|paymentId}. */
    boolean verifySignature(String orderId, String paymentId, String signature);

    /** True only for a webhook whose X-Razorpay-Signature matches HMAC-SHA256(rawBody, webhook secret). */
    boolean verifyWebhookSignature(String rawBody, String signature);

    /** The public key id Checkout needs in the browser. (The secret never leaves the server.) */
    String keyId();
}
