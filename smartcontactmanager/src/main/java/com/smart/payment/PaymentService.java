package com.smart.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smart.Dao.MyOrderRepository;
import com.smart.Dao.UserRepository;
import com.smart.entity.MyOrder;
import com.smart.entity.User;
import com.smart.payment.PaymentDtos.CreateOrderResponse;
import com.smart.payment.PaymentDtos.PaymentResponse;

/**
 * Donation business rules. Everything that decides the outcome of a payment lives here:
 * the amount is validated and fixed on the server, and an order only becomes PAID after
 * its signature has been verified.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final int MAX_REASON_LENGTH = 255;

    private final PaymentGateway gateway;
    private final MyOrderRepository orders;
    private final UserRepository users;
    private final RazorpayProperties properties;

    public PaymentService(PaymentGateway gateway, MyOrderRepository orders, UserRepository users,
            RazorpayProperties properties) {
        this.gateway = gateway;
        this.orders = orders;
        this.users = users;
        this.properties = properties;
    }

    /**
     * Creates the Razorpay order and stores it as CREATED for the logged-in user.
     * Deliberately not transactional: the HTTP call to Razorpay must not hold a DB connection.
     */
    public CreateOrderResponse createOrder(String email, BigDecimal amount) {
        User user = requireUser(email);
        long amountPaise = toPaise(amount);
        String currency = properties.currency();
        String receipt = "scm_" + UUID.randomUUID().toString().replace("-", ""); // <= 40 chars (Razorpay limit)

        GatewayOrder gatewayOrder = gateway.createOrder(amountPaise, currency, receipt);

        MyOrder order = new MyOrder();
        order.setOrderId(gatewayOrder.id());
        order.setReceipt(receipt);
        order.setUser(user);
        order.setCurrency(gatewayOrder.currency());
        order.setAmountPaise(gatewayOrder.amountPaise());
        order.setAmount(BigDecimal.valueOf(gatewayOrder.amountPaise(), 2).toPlainString()); // legacy column, in rupees
        order.setStatus(PaymentStatus.CREATED);
        orders.save(order);

        log.info("Created donation order {} for user {} ({} paise)", gatewayOrder.id(), user.getId(), amountPaise);
        return new CreateOrderResponse("created", gatewayOrder.id(), gatewayOrder.amountPaise(),
                gatewayOrder.currency(), gateway.keyId(), properties.merchantName(), user.getName(), user.getEmail());
    }

    /**
     * Marks an order PAID if, and only if, the signature is valid. Safe to call more than once:
     * the order row is locked, and a repeated call for an already paid order is a no-op.
     */
    @Transactional
    public PaymentResponse verifyPayment(String email, String orderId, String paymentId, String signature) {
        if (isBlank(orderId) || isBlank(paymentId) || isBlank(signature)) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Incomplete payment details.");
        }
        MyOrder order = loadOwnedOrder(email, orderId);

        if (PaymentStatus.isPaid(order.getStatus())) {
            if (paymentId.equals(order.getPaymentId())) {
                log.info("Order {} was already verified; ignoring duplicate verification", orderId);
                return toResponse(order, "Payment was already verified. Thank you!", true);
            }
            throw new PaymentException(HttpStatus.CONFLICT, "This order has already been paid.");
        }

        if (!gateway.verifySignature(orderId, paymentId, signature)) {
            log.warn("Invalid payment signature for order {} (user {})", orderId, email);
            throw new PaymentException(HttpStatus.BAD_REQUEST,
                    "We could not verify this payment. If money was deducted, it will be refunded by your bank "
                            + "or you can contact support with order id " + orderId + ".");
        }

        if (orders.existsByPaymentId(paymentId)) {
            log.warn("Payment id {} was already used for another order", paymentId);
            throw new PaymentException(HttpStatus.CONFLICT, "This payment has already been recorded.");
        }

        order.setPaymentId(paymentId);
        order.setStatus(PaymentStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setFailureReason(null);
        orders.save(order);

        log.info("Order {} verified and marked PAID", orderId);
        return toResponse(order, "Payment successful. Thank you for your donation of "
                + format(order.getAmountPaise(), order.getCurrency()) + "!", false);
    }

    /**
     * Reconciliation from a verified Razorpay webhook (the browser may have been closed before step 2).
     * Idempotent, and the amount and currency must match what the server created.
     *
     * @return true if the order is PAID after this call
     */
    @Transactional
    public boolean markPaidFromWebhook(String orderId, String paymentId, long amountPaise, String currency) {
        if (isBlank(orderId) || isBlank(paymentId)) {
            return false;
        }
        MyOrder order = orders.findByOrderIdForUpdate(orderId).orElse(null);
        if (order == null) {
            log.warn("Webhook for unknown order {}", orderId);
            return false;
        }
        if (PaymentStatus.isPaid(order.getStatus())) {
            return true;
        }
        if (order.getAmountPaise() == null || order.getAmountPaise() != amountPaise
                || order.getCurrency() == null || !order.getCurrency().equalsIgnoreCase(currency)) {
            log.error("Webhook amount/currency mismatch for order {}", orderId);
            return false;
        }
        if (orders.existsByPaymentId(paymentId)) {
            log.warn("Webhook payment id {} already recorded on another order", paymentId);
            return false;
        }
        order.setPaymentId(paymentId);
        order.setStatus(PaymentStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setFailureReason(null);
        orders.save(order);
        log.info("Order {} marked PAID from verified webhook", orderId);
        return true;
    }

    /**
     * Records that checkout failed or was cancelled. The browser only tells us this for
     * information: it can never turn an order into PAID, and a PAID order is never downgraded.
     * A failed order can still be paid later (Razorpay lets the user retry on the same order).
     */
    @Transactional
    public void recordFailure(String email, String orderId, boolean cancelled, String reason) {
        MyOrder order = loadOwnedOrder(email, orderId);
        if (PaymentStatus.isPaid(order.getStatus())) {
            return;
        }
        order.setStatus(cancelled ? PaymentStatus.CANCELLED : PaymentStatus.FAILED);
        order.setFailureReason(truncate(reason));
        orders.save(order);
        log.info("Order {} marked {}", orderId, order.getStatus());
    }

    // ---------------------------------------------------------------- helpers

    private User requireUser(String email) {
        User user = email == null ? null : users.findByEmail(email);
        if (user == null) {
            throw new PaymentException(HttpStatus.UNAUTHORIZED, "Please log in to donate.");
        }
        return user;
    }

    /** Loads (and row-locks) the order. Another user's order is reported as "not found". */
    private MyOrder loadOwnedOrder(String email, String orderId) {
        MyOrder order = orders.findByOrderIdForUpdate(orderId).orElseThrow(
                () -> new PaymentException(HttpStatus.NOT_FOUND, "Order not found."));
        User owner = order.getUser();
        if (owner == null || email == null || !email.equalsIgnoreCase(owner.getEmail())) {
            throw new PaymentException(HttpStatus.NOT_FOUND, "Order not found.");
        }
        return order;
    }

    private long toPaise(BigDecimal amount) {
        if (amount == null) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Amount is required.");
        }
        long paise;
        try {
            paise = amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Amount can have at most two decimal places.");
        }
        long min = properties.minAmount() * 100;
        long max = properties.maxAmount() * 100;
        if (paise < min || paise > max) {
            throw new PaymentException(HttpStatus.BAD_REQUEST,
                    "Amount must be between " + properties.minAmount() + " and " + properties.maxAmount() + ".");
        }
        return paise;
    }

    private PaymentResponse toResponse(MyOrder order, String message, boolean duplicate) {
        return new PaymentResponse("paid", message, order.getOrderId(), order.getPaymentId(),
                order.getAmountPaise() == null ? 0 : order.getAmountPaise(), order.getCurrency(), duplicate);
    }

    private static String format(Long paise, String currency) {
        String amount = BigDecimal.valueOf(paise == null ? 0 : paise, 2).toPlainString();
        return ("INR".equals(currency) ? "\u20B9" : currency + " ") + amount;
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
