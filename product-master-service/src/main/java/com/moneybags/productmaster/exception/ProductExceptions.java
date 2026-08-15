package com.moneybags.productmaster.exception;

import java.util.List;

public final class ProductExceptions {
    private ProductExceptions() {}

    public static final class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String productCode) {
            super("Product not found: " + productCode);
        }
    }

    public static final class BusinessValidationException extends RuntimeException {
        private final List<String> validationMessages;

        public BusinessValidationException(List<String> validationMessages) {
            super(String.join("; ", validationMessages));
            this.validationMessages = List.copyOf(validationMessages);
        }

        public List<String> getValidationMessages() {
            return validationMessages;
        }
    }
}
