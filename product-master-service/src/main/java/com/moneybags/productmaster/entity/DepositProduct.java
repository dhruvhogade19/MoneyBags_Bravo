package com.moneybags.productmaster.entity;

import com.moneybags.productmaster.domain.Enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "DEPOSIT_PRODUCT")
public class DepositProduct {
    @Id @Column(name = "DEPOSIT_PRODUCT_ID", length = 36) private String id;
    @Column(name = "PRODUCT_CODE", nullable = false, unique = true, updatable = false, length = 40) private String productCode;
    @Column(name = "PRODUCT_NAME", nullable = false, length = 120) private String productName;
    @Column(name = "DESCRIPTION", length = 1000) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "DEPOSIT_TYPE", nullable = false, length = 30) private Subtype subtype;
    @Column(name = "CURRENCY_CODE", nullable = false, length = 3) private String currencyCode = "INR";
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", nullable = false, length = 20) private Status status = Status.DRAFT;
    @Column(name = "EFFECTIVE_FROM", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "EFFECTIVE_TO") private LocalDate effectiveTo;
    @Column(name = "CREATED_AT", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "UPDATED_AT", nullable = false) private Instant updatedAt;
    @Column(name = "CREATED_BY", nullable = false, length = 100) private String createdBy;
    @Column(name = "UPDATED_BY", nullable = false, length = 100) private String updatedBy;

    @Embedded private CatalogRuleValues.DepositAmountRule amountRule = new CatalogRuleValues.DepositAmountRule();
    @Embedded private CatalogRuleValues.FixedDepositRule fixedDepositRule = new CatalogRuleValues.FixedDepositRule();
    @Embedded private CatalogRuleValues.AccountClosureRule accountClosureRule = new CatalogRuleValues.AccountClosureRule();
    @Embedded private CatalogRuleValues.PrematureClosureRule prematureClosureRule = new CatalogRuleValues.PrematureClosureRule();
    @Embedded private CatalogRuleValues.RenewalRule renewalRule = new CatalogRuleValues.RenewalRule();

    @OneToMany(mappedBy = "depositProduct", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("effectiveFrom DESC") private List<DepositInterestPolicy> interestPolicies = new ArrayList<>();
    @ElementCollection @CollectionTable(name = "DEPOSIT_PRODUCT_FEE", joinColumns = @JoinColumn(name = "DEPOSIT_PRODUCT_ID"))
    private List<CatalogRuleValues.Fee> fees = new ArrayList<>();
    @ElementCollection @CollectionTable(name = "DEPOSIT_PRODUCT_ELIGIBILITY", joinColumns = @JoinColumn(name = "DEPOSIT_PRODUCT_ID"))
    private List<CatalogRuleValues.Eligibility> eligibilityRules = new ArrayList<>();
    @ElementCollection @CollectionTable(name = "DEPOSIT_PRODUCT_FEATURE", joinColumns = @JoinColumn(name = "DEPOSIT_PRODUCT_ID"))
    private List<CatalogRuleValues.Feature> features = new ArrayList<>();
    @ElementCollection @CollectionTable(name = "FIXED_DEPOSIT_RATE_SLAB", joinColumns = @JoinColumn(name = "DEPOSIT_PRODUCT_ID"))
    private List<CatalogRuleValues.FixedDepositRateSlab> interestRateSlabs = new ArrayList<>();

    @PrePersist void onCreate() { if (id == null) id = UUID.randomUUID().toString(); Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public String getId() { return id; } public String getProductCode() { return productCode; } public void setProductCode(String value) { productCode = value; }
    public String getProductName() { return productName; } public void setProductName(String value) { productName = value; }
    public String getDescription() { return description; } public void setDescription(String value) { description = value; }
    public Subtype getSubtype() { return subtype; } public void setSubtype(Subtype value) { subtype = value; }
    public String getCurrencyCode() { return currencyCode; } public void setCurrencyCode(String value) { currencyCode = value; }
    public Status getStatus() { return status; } public void setStatus(Status value) { status = value; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; } public void setEffectiveFrom(LocalDate value) { effectiveFrom = value; }
    public LocalDate getEffectiveTo() { return effectiveTo; } public void setEffectiveTo(LocalDate value) { effectiveTo = value; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; } public void setCreatedBy(String value) { createdBy = value; }
    public String getUpdatedBy() { return updatedBy; } public void setUpdatedBy(String value) { updatedBy = value; }
    public CatalogRuleValues.DepositAmountRule getAmountRule() { return amountRule; } public void setAmountRule(CatalogRuleValues.DepositAmountRule value) { amountRule = value; }
    public CatalogRuleValues.FixedDepositRule getFixedDepositRule() { return fixedDepositRule; } public void setFixedDepositRule(CatalogRuleValues.FixedDepositRule value) { fixedDepositRule = value; }
    public CatalogRuleValues.AccountClosureRule getAccountClosureRule() { return accountClosureRule; } public void setAccountClosureRule(CatalogRuleValues.AccountClosureRule value) { accountClosureRule = value; }
    public CatalogRuleValues.PrematureClosureRule getPrematureClosureRule() { return prematureClosureRule; } public void setPrematureClosureRule(CatalogRuleValues.PrematureClosureRule value) { prematureClosureRule = value; }
    public CatalogRuleValues.RenewalRule getRenewalRule() { return renewalRule; } public void setRenewalRule(CatalogRuleValues.RenewalRule value) { renewalRule = value; }
    public List<DepositInterestPolicy> getInterestPolicies() { return interestPolicies; }
    public List<CatalogRuleValues.Fee> getFees() { return fees; } public List<CatalogRuleValues.Eligibility> getEligibilityRules() { return eligibilityRules; }
    public List<CatalogRuleValues.Feature> getFeatures() { return features; } public List<CatalogRuleValues.FixedDepositRateSlab> getInterestRateSlabs() { return interestRateSlabs; }
}
