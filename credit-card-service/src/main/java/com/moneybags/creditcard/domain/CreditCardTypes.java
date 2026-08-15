package com.moneybags.creditcard.domain;

public final class CreditCardTypes {
    private CreditCardTypes() {
    }

    public enum ApplicationStatus {PENDING, APPROVED, REJECTED}

    public enum EligibilityStatus {PENDING, ELIGIBLE, NOT_ELIGIBLE}

    public enum AccountStatus {ACTIVE, BLOCKED, CLOSURE_PENDING, CLOSED}

    public enum HoldStatus {HELD, CAPTURED, RELEASED}
}
