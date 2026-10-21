package com.smart.exception;

/** The new password breaks the password policy (or equals the old one). The message is safe to show. */
public class WeakPasswordException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WeakPasswordException(String message) {
        super(message);
    }
}
