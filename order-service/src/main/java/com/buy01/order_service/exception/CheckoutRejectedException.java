package com.buy01.order_service.exception;

/** Orders definitively rejected the request before creating an order. */
public class CheckoutRejectedException extends ConflictException {
    public CheckoutRejectedException(String message) {
        super(message);
    }
}
