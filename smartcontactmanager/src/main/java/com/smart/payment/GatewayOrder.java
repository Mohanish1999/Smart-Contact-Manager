package com.smart.payment;

/** An order as returned by the payment provider. Amount is in the smallest currency unit (paise). */
public record GatewayOrder(String id, long amountPaise, String currency) {
}
