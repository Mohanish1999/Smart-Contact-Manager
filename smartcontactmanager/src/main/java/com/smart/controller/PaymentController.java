package com.smart.controller;

import java.security.Principal;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smart.payment.PaymentDtos.ApiMessage;
import com.smart.payment.PaymentDtos.CreateOrderRequest;
import com.smart.payment.PaymentDtos.CreateOrderResponse;
import com.smart.payment.PaymentDtos.PaymentFailureRequest;
import com.smart.payment.PaymentDtos.PaymentResponse;
import com.smart.payment.PaymentDtos.VerifyPaymentRequest;
import com.smart.payment.PaymentService;

import jakarta.validation.Valid;

/**
 * Donation endpoints. They keep the URLs the dashboard JavaScript already used
 * (/user/create_order and /user/update_order); /user is protected by ROLE_USER in MyConfig.
 */
@RestController
@RequestMapping("/user")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Step 1: the browser sends only the amount; the server creates the Razorpay order. */
    @PostMapping("/create_order")
    public CreateOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request, Principal principal) {
        return paymentService.createOrder(principal.getName(), request.amount());
    }

    /** Step 2: after Checkout succeeds, the server verifies the signature before marking the order PAID. */
    @PostMapping("/update_order")
    public PaymentResponse verifyPayment(@Valid @RequestBody VerifyPaymentRequest request, Principal principal) {
        return paymentService.verifyPayment(principal.getName(), request.orderId(), request.paymentId(),
                request.signature());
    }

    /** Informational only: records a failed or cancelled checkout. Can never mark an order as paid. */
    @PostMapping("/payment_failed")
    public ApiMessage paymentFailed(@Valid @RequestBody PaymentFailureRequest request, Principal principal) {
        paymentService.recordFailure(principal.getName(), request.orderId(), request.cancelled(), request.reason());
        return new ApiMessage("ok", "Payment status recorded.");
    }
}
