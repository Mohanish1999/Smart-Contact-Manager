package com.smart.exception;

/** The current password supplied by the user is wrong. */
public class InvalidPasswordException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidPasswordException(String message) {
        super(message);
    }
}
