package com.smart.exception;

/** The uploaded file was rejected (wrong type, too large, unreadable). The message is safe to show. */
public class InvalidUploadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidUploadException(String message) {
        super(message);
    }
}
