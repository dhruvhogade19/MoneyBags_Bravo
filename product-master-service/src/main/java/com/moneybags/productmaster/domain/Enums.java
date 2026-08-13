package com.moneybags.productmaster.domain;

public final class Enums {
    private Enums() {}

    public enum Category { DEPOSIT, CREDIT_CARD }
    public enum Subtype { SAVINGS, CURRENT, FIXED_DEPOSIT, CREDIT_CARD }
    public enum Status { DRAFT, ACTIVE, INACTIVE, DISCONTINUED }
    public enum InterestCalculationMethod {
        SIMPLE, COMPOUND, COMPOUND_INTEREST, REDUCING_BALANCE, DAILY_BALANCE
    }
    public enum InterestFrequency { DAILY, MONTHLY, QUARTERLY, ANNUALLY }
    public enum InterestPostingFrequency { DAILY, MONTHLY, QUARTERLY, ANNUALLY, AT_MATURITY }
    public enum InterestType { CREDIT, DEBIT }
    public enum PricingMode { FIXED, BENCHMARK_PLUS_SPREAD }
    public enum DayCountConvention { ACTUAL_365, ACTUAL_360, THIRTY_360 }
    public enum RateApplicationMethod { BOOKING_DATE, MATURITY_DATE_RATE }
    public enum CustomerCategory { REGULAR, SENIOR_CITIZEN, ANY }
    public enum FeeType { ACCOUNT_OPENING, MAINTENANCE, TRANSACTION, PROCESSING, PREPAYMENT, LATE_PAYMENT, ANNUAL_MEMBERSHIP, CASH_ADVANCE, OVER_LIMIT }
    public enum FeeFrequency { ONE_TIME, MONTHLY, ANNUALLY, PER_TRANSACTION }
    public enum CustomerType { INDIVIDUAL, BUSINESS, ANY }
}
