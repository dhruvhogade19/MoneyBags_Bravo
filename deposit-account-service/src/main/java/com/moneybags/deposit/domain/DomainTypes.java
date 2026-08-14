package com.moneybags.deposit.domain;

public final class DomainTypes {
    private DomainTypes() {}

    public enum AccountStatus {
        PENDING_ACTIVATION, ACTIVE, BLOCKED, FROZEN, DORMANT, CLOSURE_PENDING, CLOSED
    }

    public enum ProductSubtype { SAVINGS, CURRENT, FIXED_DEPOSIT }

    public enum FixedDepositStatus {
        PENDING_FUNDING, ACTIVE, FUNDING_FAILED, MATURED, PREMATURE_CLOSURE_REQUESTED,
        PAYOUT_PENDING, PAID_OUT, CLOSED_PREMATURE
    }

    public enum ClosureType { CASA_CUSTOMER_REQUEST, FD_MATURITY, FD_PREMATURE }

    public enum ClosureRequestStatus {
        REQUESTED, VALIDATING, REJECTED, SETTLEMENT_PENDING, READY_TO_CLOSE,
        PAYOUT_PENDING, CLOSED, SETTLEMENT_FAILED, CANCELLED
    }

    public enum ClosureCheckStatus { PASSED, FAILED }

    public enum ClosureSettlementStatus { PENDING, COMPLETED, FAILED }

    public enum TenureUnit { DAY, MONTH }

    public enum CompoundingFrequency { MONTHLY, QUARTERLY, HALF_YEARLY, ANNUALLY }

    public enum InterestPayoutFrequency { AT_MATURITY }

    public enum DayCountConvention { ACTUAL_365 }

    public enum FixedDepositPayoutStatus { PENDING, COMPLETED, FAILED }

    public enum HolderRole { PRIMARY, JOINT, AUTHORIZED }

    public enum OperatingInstruction { SINGLE, JOINTLY, EITHER_OR_SURVIVOR, ANYONE_OR_SURVIVOR }

    public enum LimitType { DAILY_DEBIT, DAILY_CREDIT, SINGLE_TRANSACTION, CHANNEL_TRANSFER }

    public enum RecordStatus { ACTIVE, INACTIVE, REVOKED }

    public enum PaymentOperationType {
        BOOK_TRANSFER, CREDIT_CARD_REPAYMENT, FIXED_DEPOSIT_FUNDING, FIXED_DEPOSIT_MATURITY_PAYOUT,
        CASA_ACCOUNT_CLOSURE, FIXED_DEPOSIT_PREMATURE_PAYOUT
    }

    public enum ReservationStatus { ACTIVE, CAPTURED, SETTLED, RELEASED, EXPIRED }

    public enum DepositTransactionType { PAYMENT_HOLD, HOLD_RELEASE, DEBIT, CREDIT }
}
