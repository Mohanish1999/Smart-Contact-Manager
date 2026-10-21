package com.smart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.smart.payment.PaymentDtos.CreateOrderResponse;
import com.smart.payment.PaymentDtos.PaymentResponse;
import com.smart.payment.PaymentException;
import com.smart.payment.PaymentService;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    private static final String EMAIL = "asha@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    void createOrderReturnsTheServerCreatedOrder() throws Exception {
        when(paymentService.createOrder(eq(EMAIL), any(BigDecimal.class))).thenReturn(
                new CreateOrderResponse("created", "order_1", 25000, "INR", "rzp_test_abc", "SCM", "Asha", EMAIL));

        mockMvc.perform(post("/user/create_order").principal(() -> EMAIL)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("created"))
                .andExpect(jsonPath("$.id").value("order_1"))
                .andExpect(jsonPath("$.amount").value(25000))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.keyId").value("rzp_test_abc"));

        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentService).createOrder(eq(EMAIL), amount.capture());
        org.junit.jupiter.api.Assertions.assertEquals(0, amount.getValue().compareTo(new BigDecimal("250")));
    }

    @Test
    void createOrderIgnoresAnythingButTheAmount() throws Exception {
        when(paymentService.createOrder(eq(EMAIL), any(BigDecimal.class))).thenReturn(
                new CreateOrderResponse("created", "order_1", 10000, "INR", "k", "SCM", "Asha", EMAIL));

        // A tampered client cannot choose the currency, status or user.
        mockMvc.perform(post("/user/create_order").principal(() -> EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100,\"currency\":\"USD\",\"status\":\"paid\",\"user\":\"someone-else\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void createOrderRejectsMissingAmount() throws Exception {
        mockMvc.perform(post("/user/create_order").principal(() -> EMAIL)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.msg").value("Amount is required"));
    }

    @Test
    void createOrderRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/user/create_order").principal(() -> EMAIL)
                .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void createOrderShowsBusinessErrorsAsJson() throws Exception {
        when(paymentService.createOrder(eq(EMAIL), any(BigDecimal.class)))
                .thenThrow(new PaymentException(HttpStatus.BAD_REQUEST, "Amount must be between 1 and 100000."));

        mockMvc.perform(post("/user/create_order").principal(() -> EMAIL)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("Amount must be between 1 and 100000."));
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() throws Exception {
        when(paymentService.createOrder(eq(EMAIL), any(BigDecimal.class))).thenThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(post("/user/create_order").principal(() -> EMAIL)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.msg").value("Something went wrong. Please try again."));
    }

    @Test
    void verifyPaymentSuccess() throws Exception {
        when(paymentService.verifyPayment(EMAIL, "order_1", "pay_1", "sig")).thenReturn(
                new PaymentResponse("paid", "Payment successful.", "order_1", "pay_1", 25000, "INR", false));

        mockMvc.perform(post("/user/update_order").principal(() -> EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"razorpay_order_id\":\"order_1\",\"razorpay_payment_id\":\"pay_1\",\"razorpay_signature\":\"sig\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paid"))
                .andExpect(jsonPath("$.msg").value("Payment successful."))
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void verifyPaymentWithInvalidSignatureIsABadRequest() throws Exception {
        when(paymentService.verifyPayment(EMAIL, "order_1", "pay_1", "forged"))
                .thenThrow(new PaymentException(HttpStatus.BAD_REQUEST, "We could not verify this payment."));

        mockMvc.perform(post("/user/update_order").principal(() -> EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"razorpay_order_id\":\"order_1\",\"razorpay_payment_id\":\"pay_1\",\"razorpay_signature\":\"forged\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.msg").value("We could not verify this payment."));
    }

    @Test
    void verifyPaymentRequiresAllFields() throws Exception {
        mockMvc.perform(post("/user/update_order").principal(() -> EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"razorpay_order_id\":\"order_1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paymentFailedIsRecorded() throws Exception {
        mockMvc.perform(post("/user/payment_failed").principal(() -> EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"razorpay_order_id\":\"order_1\",\"cancelled\":true,\"reason\":\"Checkout closed by the user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(paymentService).recordFailure(EMAIL, "order_1", true, "Checkout closed by the user");
    }
}
