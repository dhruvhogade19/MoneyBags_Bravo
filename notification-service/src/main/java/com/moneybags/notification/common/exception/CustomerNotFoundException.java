package com.moneybags.notification.common.exception;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long cifId) {
        super("Customer was not found for cifId: " + cifId);
    }
}
