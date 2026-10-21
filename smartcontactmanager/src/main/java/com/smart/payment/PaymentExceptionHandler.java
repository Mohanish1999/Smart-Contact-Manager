package com.smart.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.smart.controller.PaymentController;
import com.smart.payment.PaymentDtos.ApiMessage;

/** Turns payment errors into clear JSON for the dashboard JavaScript. Applies to PaymentController only. */
@RestControllerAdvice(assignableTypes = PaymentController.class)
public class PaymentExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExceptionHandler.class);

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiMessage> handlePayment(PaymentException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiMessage("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiMessage> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage()).findFirst().orElse("Invalid request.");
        return ResponseEntity.badRequest().body(new ApiMessage("error", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiMessage> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ApiMessage("error", "Invalid request."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiMessage> handleUnexpected(Exception ex) {
        log.error("Unexpected payment error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiMessage("error", "Something went wrong. Please try again."));
    }
}
