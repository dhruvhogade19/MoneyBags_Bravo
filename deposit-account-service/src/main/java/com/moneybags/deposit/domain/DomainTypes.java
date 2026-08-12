package com.moneybags.deposit.domain;

public final class DomainTypes {
    private DomainTypes() {}

    public enum AccountStatus {
        PENDING_ACTIVATION, ACTIVE, BLOCKED, FROZEN, DORMANT, CLOSURE_PENDING, CLOSED
    }

    public enum HolderRole { PRIMARY, JOINT, AUTHORIZED }

    public enum OperatingInstruction { SINGLE, JOINTLY, EITHER_OR_SURVIVOR, ANYONE_OR_SURVIVOR }

    public enum LimitType { DAILY_DEBIT, DAILY_CREDIT, SINGLE_TRANSACTION, CHANNEL_TRANSFER }

    public enum RecordStatus { ACTIVE, INACTIVE, REVOKED }

    public enum PaymentOperationType { BOOK_TRANSFER, CREDIT_CARD_REPAYMENT }

    public enum ReservationStatus { ACTIVE, CAPTURED, SETTLED, RELEASED, EXPIRED }

    public enum DepositTransactionType { PAYMENT_HOLD, HOLD_RELEASE, DEBIT, CREDIT }
}
