package com.smart.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.smart.Dao.MyOrderRepository;
import com.smart.Dao.UserRepository;
import com.smart.entity.MyOrder;
import com.smart.entity.User;
import com.smart.payment.PaymentDtos.CreateOrderResponse;
import com.smart.payment.PaymentDtos.PaymentResponse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    private static final String SECRET = "my_test_secret";
    private static final String EMAIL = "asha@example.com";

    @Mock
    private PaymentGateway gateway;
    @Mock
    private MyOrderRepository orders;
    @Mock
    private UserRepository users;

    private PaymentService service;
    private User asha;

    @BeforeEach
    void setUp() {
        RazorpayProperties props = new RazorpayProperties("rzp_test_abc", SECRET, "INR", 1, 100000, "Smart Contact Manager", "whsec_test");
        service = new PaymentService(gateway, orders, users, props);

        asha = user(1, EMAIL);
        when(users.findByEmail(EMAIL)).thenReturn(asha);
        when(orders.save(any(MyOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gateway.keyId()).thenReturn("rzp_test_abc");
        // The mocked gateway still performs the REAL HMAC check, just without Razorpay's network.
        when(gateway.verifySignature(anyString(), anyString(), anyString())).thenAnswer(invocation ->
                RazorpaySignature.isValid(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), SECRET));
    }

    // ------------------------------------------------------------ order creation

    @Test
    void createOrderStoresCreatedOrderForTheLoggedInUser() {
        when(gateway.createOrder(anyLong(), anyString(), anyString())).thenReturn(new GatewayOrder("order_1", 25000, "INR"));

        CreateOrderResponse response = service.createOrder(EMAIL, new BigDecimal("250"));

        assertEquals("created", response.status());
        assertEquals("order_1", response.id());
        assertEquals(25000, response.amount());
        assertEquals("INR", response.currency());
        assertEquals("rzp_test_abc", response.keyId());

        ArgumentCaptor<MyOrder> saved = ArgumentCaptor.forClass(MyOrder.class);
        verify(orders).save(saved.capture());
        MyOrder order = saved.getValue();
        assertEquals("order_1", order.getOrderId());
        assertEquals(PaymentStatus.CREATED, order.getStatus());
        assertEquals(25000L, order.getAmountPaise());
        assertEquals("250.00", order.getAmount());
        assertEquals(asha, order.getUser());
        assertTrue(order.getReceipt().length() <= 40, "Razorpay receipts are limited to 40 characters");
    }

    @Test
    void createOrderSendsPaiseAndConfiguredCurrencyToTheGateway() {
        when(gateway.createOrder(anyLong(), anyString(), anyString())).thenReturn(new GatewayOrder("order_1", 1050, "INR"));

        service.createOrder(EMAIL, new BigDecimal("10.50"));

        verify(gateway).createOrder(org.mockito.ArgumentMatchers.eq(1050L), org.mockito.ArgumentMatchers.eq("INR"), anyString());
    }

    @Test
    void createOrderRejectsInvalidAmountsWithoutCallingRazorpay() {
        for (BigDecimal bad : new BigDecimal[] { null, BigDecimal.ZERO, new BigDecimal("-5"), new BigDecimal("0.50"),
                new BigDecimal("100001"), new BigDecimal("1.234") }) {
            PaymentException ex = assertThrows(PaymentException.class, () -> service.createOrder(EMAIL, bad), "amount " + bad);
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
        verify(gateway, never()).createOrder(anyLong(), anyString(), anyString());
        verify(orders, never()).save(any(MyOrder.class));
    }

    @Test
    void createOrderRequiresAKnownUser() {
        PaymentException ex = assertThrows(PaymentException.class, () -> service.createOrder("nobody@example.com", new BigDecimal("100")));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(gateway, never()).createOrder(anyLong(), anyString(), anyString());
    }

    @Test
    void createOrderDoesNotStoreAnythingWhenRazorpayFails() {
        when(gateway.createOrder(anyLong(), anyString(), anyString()))
                .thenThrow(new PaymentException(HttpStatus.BAD_GATEWAY, "down"));

        assertThrows(PaymentException.class, () -> service.createOrder(EMAIL, new BigDecimal("100")));
        verify(orders, never()).save(any(MyOrder.class));
    }

    // ------------------------------------------------------------ verification

    @Test
    void validSignatureMarksOrderPaid() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.CREATED);
        String signature = RazorpaySignature.compute("order_1", "pay_1", SECRET);

        PaymentResponse response = service.verifyPayment(EMAIL, "order_1", "pay_1", signature);

        assertEquals("paid", response.status());
        assertFalse(response.duplicate());
        assertEquals(PaymentStatus.PAID, order.getStatus());
        assertEquals("pay_1", order.getPaymentId());
        assertNotNull(order.getPaidAt());
        verify(orders).save(order);
    }

    @Test
    void invalidSignatureIsRejectedAndOrderStaysUnpaid() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.CREATED);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> service.verifyPayment(EMAIL, "order_1", "pay_1", "forged-signature"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(PaymentStatus.CREATED, order.getStatus());
        assertNull(order.getPaymentId());
        verify(orders, never()).save(any(MyOrder.class));
    }

    @Test
    void signatureForAnotherPaymentIdIsRejected() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.CREATED);
        String signatureForPay1 = RazorpaySignature.compute("order_1", "pay_1", SECRET);

        assertThrows(PaymentException.class, () -> service.verifyPayment(EMAIL, "order_1", "pay_999", signatureForPay1));
        assertEquals(PaymentStatus.CREATED, order.getStatus());
    }

    @Test
    void duplicateVerificationOfAPaidOrderIsIdempotent() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.PAID);
        order.setPaymentId("pay_1");
        String signature = RazorpaySignature.compute("order_1", "pay_1", SECRET);

        PaymentResponse response = service.verifyPayment(EMAIL, "order_1", "pay_1", signature);

        assertTrue(response.duplicate());
        assertEquals(PaymentStatus.PAID, order.getStatus());
        verify(orders, never()).save(any(MyOrder.class));
    }

    @Test
    void aPaidOrderCannotBePaidAgainWithADifferentPaymentId() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.PAID);
        order.setPaymentId("pay_1");
        String signature = RazorpaySignature.compute("order_1", "pay_2", SECRET);

        PaymentException ex = assertThrows(PaymentException.class, () -> service.verifyPayment(EMAIL, "order_1", "pay_2", signature));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("pay_1", order.getPaymentId());
    }

    @Test
    void aPaymentIdAlreadyUsedByAnotherOrderIsRejected() {
        MyOrder order = existingOrder("order_2", asha, PaymentStatus.CREATED);
        when(orders.existsByPaymentId("pay_1")).thenReturn(true);
        String signature = RazorpaySignature.compute("order_2", "pay_1", SECRET);

        PaymentException ex = assertThrows(PaymentException.class, () -> service.verifyPayment(EMAIL, "order_2", "pay_1", signature));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(PaymentStatus.CREATED, order.getStatus());
    }

    @Test
    void anotherUsersOrderLooksLikeItDoesNotExist() {
        existingOrder("order_1", user(2, "bob@example.com"), PaymentStatus.CREATED);
        String signature = RazorpaySignature.compute("order_1", "pay_1", SECRET);

        PaymentException ex = assertThrows(PaymentException.class, () -> service.verifyPayment(EMAIL, "order_1", "pay_1", signature));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void unknownOrderIsNotFound() {
        when(orders.findByOrderIdForUpdate("order_x")).thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class, () -> service.verifyPayment(EMAIL, "order_x", "pay_1", "sig"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void incompleteDetailsAreRejected() {
        PaymentException ex = assertThrows(PaymentException.class, () -> service.verifyPayment(EMAIL, "order_1", " ", "sig"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // ------------------------------------------------------------ failures and cancellations

    @Test
    void failedCheckoutIsRecordedWithItsReason() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.CREATED);

        service.recordFailure(EMAIL, "order_1", false, "Card declined");

        assertEquals(PaymentStatus.FAILED, order.getStatus());
        assertEquals("Card declined", order.getFailureReason());
        verify(orders).save(order);
    }

    @Test
    void cancelledCheckoutIsRecordedAsCancelled() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.CREATED);

        service.recordFailure(EMAIL, "order_1", true, "Checkout closed by the user");

        assertEquals(PaymentStatus.CANCELLED, order.getStatus());
    }

    @Test
    void aFailureReportCanNeverDowngradeAPaidOrder() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.PAID);

        service.recordFailure(EMAIL, "order_1", false, "forged report");

        assertEquals(PaymentStatus.PAID, order.getStatus());
        verify(orders, never()).save(any(MyOrder.class));
    }

    @Test
    void aFailedOrderCanStillBePaidAfterARetry() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.FAILED);
        order.setFailureReason("Card declined");
        String signature = RazorpaySignature.compute("order_1", "pay_1", SECRET);

        service.verifyPayment(EMAIL, "order_1", "pay_1", signature);

        assertEquals(PaymentStatus.PAID, order.getStatus());
        assertNull(order.getFailureReason());
    }

    @Test
    void veryLongFailureReasonsAreTruncated() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.CREATED);

        service.recordFailure(EMAIL, "order_1", false, "x".repeat(1000));

        assertEquals(255, order.getFailureReason().length());
    }

    // ------------------------------------------------------------ webhook reconciliation

    @Test
    void webhookMarksAMatchingOrderPaid() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.CREATED);

        assertTrue(service.markPaidFromWebhook("order_1", "pay_1", 25000, "INR"));

        assertEquals(PaymentStatus.PAID, order.getStatus());
        assertEquals("pay_1", order.getPaymentId());
        verify(orders).save(order);
    }

    @Test
    void webhookIsIdempotent() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.PAID);
        order.setPaymentId("pay_1");

        assertTrue(service.markPaidFromWebhook("order_1", "pay_1", 25000, "INR"));
        verify(orders, never()).save(any(MyOrder.class));
    }

    @Test
    void webhookWithAWrongAmountOrCurrencyDoesNotMarkPaid() {
        MyOrder order = existingOrder("order_1", asha, PaymentStatus.CREATED);

        assertFalse(service.markPaidFromWebhook("order_1", "pay_1", 100, "INR"));
        assertFalse(service.markPaidFromWebhook("order_1", "pay_1", 25000, "USD"));

        assertEquals(PaymentStatus.CREATED, order.getStatus());
        verify(orders, never()).save(any(MyOrder.class));
    }

    @Test
    void webhookForAnUnknownOrderOrReusedPaymentIdIsIgnored() {
        when(orders.findByOrderIdForUpdate("order_x")).thenReturn(Optional.empty());
        assertFalse(service.markPaidFromWebhook("order_x", "pay_1", 25000, "INR"));

        MyOrder order = existingOrder("order_2", asha, PaymentStatus.CREATED);
        when(orders.existsByPaymentId("pay_9")).thenReturn(true);
        assertFalse(service.markPaidFromWebhook("order_2", "pay_9", 25000, "INR"));
        assertEquals(PaymentStatus.CREATED, order.getStatus());
    }

    // ------------------------------------------------------------ helpers

    private MyOrder existingOrder(String orderId, User owner, String status) {
        MyOrder order = new MyOrder();
        order.setOrderId(orderId);
        order.setUser(owner);
        order.setStatus(status);
        order.setCurrency("INR");
        order.setAmountPaise(25000L);
        when(orders.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(order));
        return order;
    }

    private static User user(int id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName("User " + id);
        return user;
    }
}
