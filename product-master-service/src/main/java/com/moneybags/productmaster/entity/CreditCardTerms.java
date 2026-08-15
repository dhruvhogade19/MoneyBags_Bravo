package com.moneybags.productmaster.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.type.NumericBooleanConverter;

@Entity
@Table(name = "CREDIT_CARD_TERMS")
public class CreditCardTerms {
    @Id @Column(name = "CREDIT_CARD_TERMS_ID", length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "CREDIT_CARD_PRODUCT_ID", nullable = false) private CreditCardProduct creditCardProduct;
    @Column(name = "POLICY_VERSION", nullable = false, length = 40) private String policyVersion = "V1";
    @Column(name = "EFFECTIVE_FROM", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "EFFECTIVE_TO") private LocalDate effectiveTo;
    @Column(name = "MINIMUM_CREDIT_LIMIT", nullable = false, precision = 19, scale = 4) private BigDecimal minimumCreditLimit;
    @Column(name = "MAXIMUM_CREDIT_LIMIT", nullable = false, precision = 19, scale = 4) private BigDecimal maximumCreditLimit;
    @Column(name = "INTEREST_FREE_DAYS", nullable = false) private Integer interestFreeDays;
    @Column(name = "MINIMUM_PAYMENT_PCT", nullable = false, precision = 9, scale = 4) private BigDecimal minimumPaymentPercentage;
    @Column(name = "MINIMUM_PAYMENT_AMOUNT", nullable = false, precision = 19, scale = 4) private BigDecimal minimumPaymentAmount;
    @Column(name = "PAYMENT_DUE_DAYS", nullable = false) private Integer paymentDueDays;
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "CASH_ADVANCE_ALLOWED", nullable = false, columnDefinition = "NUMBER(1)") private boolean cashAdvanceAllowed;
    @Column(name = "CASH_ADVANCE_LIMIT_PCT", precision = 9, scale = 4) private BigDecimal cashAdvanceLimitPercentage;
    @PrePersist void onCreate() { if (id == null) id = UUID.randomUUID().toString(); policyVersion = "V1"; }
    public String getId() { return id; } public CreditCardProduct getCreditCardProduct() { return creditCardProduct; } public void setCreditCardProduct(CreditCardProduct value) { creditCardProduct = value; }
    public String getPolicyVersion() { return policyVersion; } public void setPolicyVersion(String ignored) { policyVersion = "V1"; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; } public void setEffectiveFrom(LocalDate value) { effectiveFrom = value; }
    public LocalDate getEffectiveTo() { return effectiveTo; } public void setEffectiveTo(LocalDate value) { effectiveTo = value; }
    public BigDecimal getMinimumCreditLimit() { return minimumCreditLimit; } public void setMinimumCreditLimit(BigDecimal value) { minimumCreditLimit = value; }
    public BigDecimal getMaximumCreditLimit() { return maximumCreditLimit; } public void setMaximumCreditLimit(BigDecimal value) { maximumCreditLimit = value; }
    public Integer getInterestFreeDays() { return interestFreeDays; } public void setInterestFreeDays(Integer value) { interestFreeDays = value; }
    public BigDecimal getMinimumPaymentPercentage() { return minimumPaymentPercentage; } public void setMinimumPaymentPercentage(BigDecimal value) { minimumPaymentPercentage = value; }
    public BigDecimal getMinimumPaymentAmount() { return minimumPaymentAmount; } public void setMinimumPaymentAmount(BigDecimal value) { minimumPaymentAmount = value; }
    public Integer getPaymentDueDays() { return paymentDueDays; } public void setPaymentDueDays(Integer value) { paymentDueDays = value; }
    public boolean isCashAdvanceAllowed() { return cashAdvanceAllowed; } public void setCashAdvanceAllowed(boolean value) { cashAdvanceAllowed = value; }
    public BigDecimal getCashAdvanceLimitPercentage() { return cashAdvanceLimitPercentage; } public void setCashAdvanceLimitPercentage(BigDecimal value) { cashAdvanceLimitPercentage = value; }
}
