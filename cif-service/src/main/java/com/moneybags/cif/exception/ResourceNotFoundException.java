package com.moneybags.cif.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

// This exception is used when a CIF ID does not exist.
// For example, if another service calls: GET /api/v1/cifs/9999/credit-card-details
//but CIF 9999 is not in Oracle, the service layer throws:
//        throw new ResourceNotFoundException("CIF not found with id: " + cifId);

