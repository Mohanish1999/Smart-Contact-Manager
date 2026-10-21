package com.smart.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.smart.payment.PaymentException;
import com.smart.payment.RazorpayWebhookHandler;

@WebMvcTest(WebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RazorpayWebhookHandler handler;

    @Test
    void passesTheRawBodyAndSignatureToTheHandler() throws Exception {
        mockMvc.perform(post("/razorpay/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", "sig").content("{\"event\":\"x\"}"))
                .andExpect(status().isOk());

        verify(handler).handle("{\"event\":\"x\"}", "sig");
    }

    @Test
    void invalidSignatureIsUnauthorized() throws Exception {
        doThrow(new PaymentException(HttpStatus.UNAUTHORIZED, "Invalid signature."))
                .when(handler).handle("{}", "forged");

        mockMvc.perform(post("/razorpay/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", "forged").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }
}
