package com.moneybags.cif.exception;

public class InvalidCifRequestException extends RuntimeException {

    public InvalidCifRequestException(String message) {
        super(message);
    }
}
