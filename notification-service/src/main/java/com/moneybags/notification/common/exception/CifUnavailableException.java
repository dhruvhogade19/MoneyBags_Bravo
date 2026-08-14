package com.moneybags.notification.common.exception;

public class CifUnavailableException extends RuntimeException {

    public CifUnavailableException(Long cifId, Throwable cause) {
        super("CIF service is unavailable for cifId: " + cifId, cause);
    }
}
