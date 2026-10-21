package com.smart.payment;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request/response bodies of the payment endpoints. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** Only the amount is accepted from the browser: never status, currency, user or order id. */
    public record CreateOrderRequest(
            @JsonProperty("amount") @NotNull(message = "Amount is required") BigDecimal amount) {
    }

    /** {@code amount} is in paise, exactly what Checkout expects. */
    public record CreateOrderResponse(String status, String id, long amount, String currency,
            String keyId, String merchantName, String name, String email) {
    }

    public record VerifyPaymentRequest(
            @JsonProperty("razorpay_order_id") @NotBlank(message = "Order id is required") String orderId,
            @JsonProperty("razorpay_payment_id") @NotBlank(message = "Payment id is required") String paymentId,
            @JsonProperty("razorpay_signature") @NotBlank(message = "Signature is required") String signature) {
    }

    public record PaymentFailureRequest(
            @JsonProperty("razorpay_order_id") @NotBlank(message = "Order id is required") String orderId,
            @JsonProperty("cancelled") boolean cancelled,
            @JsonProperty("reason") String reason) {
    }

    public record PaymentResponse(String status, String msg, String orderId, String paymentId,
            long amount, String currency, boolean duplicate) {
    }

    public record ApiMessage(String status, String msg) {
    }
}
