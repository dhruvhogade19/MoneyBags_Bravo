package com.moneybags.notification.common.exception;

public class InvalidCustomerEmailException extends RuntimeException {

    public InvalidCustomerEmailException(Long cifId) {
        super("Customer has no valid email address for cifId: " + cifId);
    }
}
