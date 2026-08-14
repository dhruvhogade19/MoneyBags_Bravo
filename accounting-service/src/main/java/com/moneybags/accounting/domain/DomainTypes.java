package com.moneybags.accounting.domain;

public final class DomainTypes {
    private DomainTypes() {}

    public enum GlAccountType { ASSET, LIABILITY, EQUITY, INCOME, EXPENSE }
    public enum NormalBalance { DEBIT, CREDIT }
    public enum RecordStatus { ACTIVE, INACTIVE }
    public enum PostingStatus { RECEIVED, POSTED, REJECTED }
    public enum JournalStatus { POSTED }
    public enum AccountType { DEPOSIT_ACCOUNT, CREDIT_CARD_ACCOUNT }
    public enum LifecycleState { OPEN, CLOSED }
    public enum LifecycleEventType {
        DEPOSIT_ACCOUNT_OPENED,
        DEPOSIT_ACCOUNT_CLOSED,
        CREDIT_CARD_ACCOUNT_OPENED,
        CREDIT_CARD_ACCOUNT_CLOSED
    }
    public enum PeriodStatus { OPEN, CLOSING, CLOSED }
    public enum ReconciliationStatus { MATCHED, EXCEPTION, RESOLVED }
    public enum ReconciliationItemStatus { OPEN, RESOLVED, ACCEPTED }
}
