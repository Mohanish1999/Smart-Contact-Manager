package com.smart.exception;

/** A record that does not exist, or that belongs to someone else (deliberately indistinguishable). */
public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
