package com.moneybags.cif.exception;

public class KycServiceUnavailableException extends RuntimeException {

    public KycServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

//This represents a communication problem between CIF and KYC Service