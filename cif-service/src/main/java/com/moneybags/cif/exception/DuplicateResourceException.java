package com.moneybags.cif.exception;

public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}

//Use this when a customer tries to create or update a CIF with an email, phone number, PAN, or Aadhaar number that is already registered.