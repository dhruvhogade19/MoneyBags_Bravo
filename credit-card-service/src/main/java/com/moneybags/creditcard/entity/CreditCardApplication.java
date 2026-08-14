package com.moneybags.creditcard.entity;

import com.moneybags.creditcard.domain.CreditCardTypes.ApplicationStatus;
import com.moneybags.creditcard.domain.CreditCardTypes.EligibilityStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CREDIT_CARD_APPLICATION")
public class CreditCardApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "APPLICATION_ID")
    public Long id;

    @Column(name = "CIF_ID", nullable = false)
    public Long cifId;

    @Column(name = "PRODUCT_CODE", nullable = false)
    public String productCode;

    @Column(name = "REQUESTED_CREDIT_LIMIT", nullable = false, precision = 19, scale = 2)
    public BigDecimal requestedCreditLimit;

    @Column(name = "APPROVED_CREDIT_LIMIT", precision = 19, scale = 2)
    public BigDecimal approvedCreditLimit;

    @Column(name = "PURCHASE_INTEREST_RATE_SNAPSHOT", precision = 9, scale = 4)
    public BigDecimal purchaseInterestRateSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "APPLICATION_STATUS", nullable = false)
    public ApplicationStatus applicationStatus;

    @Column(name = "KYC_STATUS_SNAPSHOT")
    public String kycStatusSnapshot;

    @Column(name = "AGE")
    public Integer age;

    @Column(name = "SALARY", precision = 19, scale = 2)
    public BigDecimal salary;

    @Enumerated(EnumType.STRING)
    @Column(name = "ELIGIBILITY_STATUS", nullable = false)
    public EligibilityStatus eligibilityStatus;

    @Column(name = "SUBMITTED_AT", nullable = false)
    public OffsetDateTime submittedAt;

    @Column(name = "UPDATED_AT", nullable = false)
    public OffsetDateTime updatedAt;
}
