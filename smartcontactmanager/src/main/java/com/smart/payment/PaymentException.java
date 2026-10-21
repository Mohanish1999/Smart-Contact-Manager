package com.smart.payment;

import org.springframework.http.HttpStatus;

/** A payment problem that can safely be shown to the user (message) with a matching HTTP status. */
public class PaymentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    public PaymentException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
