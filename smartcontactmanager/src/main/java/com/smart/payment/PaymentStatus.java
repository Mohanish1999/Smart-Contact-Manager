package com.smart.payment;

/** Values stored in {@code orders.status}. Comparison is case-insensitive because older rows are lower-case. */
public final class PaymentStatus {

    public static final String CREATED = "CREATED";
    public static final String PAID = "PAID";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";

    private PaymentStatus() {
    }

    public static boolean isPaid(String status) {
        return PAID.equalsIgnoreCase(status);
    }
}
