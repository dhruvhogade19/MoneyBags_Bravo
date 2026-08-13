package com.moneybags.productmaster.entity;

import com.moneybags.productmaster.domain.Enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "CREDIT_CARD_PRODUCT")
public class CreditCardProduct {
    @Id @Column(name = "CREDIT_CARD_PRODUCT_ID", length = 36) private String id;
    @Column(name = "PRODUCT_CODE", nullable = false, unique = true, updatable = false, length = 40) private String productCode;
    @Column(name = "PRODUCT_NAME", nullable = false, length = 120) private String productName;
    @Column(name = "DESCRIPTION", length = 1000) private String description;
    @Column(name = "CURRENCY_CODE", nullable = false, length = 3) private String currencyCode = "INR";
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", nullable = false, length = 20) private Status status = Status.DRAFT;
    @Column(name = "EFFECTIVE_FROM", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "EFFECTIVE_TO") private LocalDate effectiveTo;
    @Column(name = "CREATED_AT", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "UPDATED_AT", nullable = false) private Instant updatedAt;
    @Column(name = "CREATED_BY", nullable = false, length = 100) private String createdBy;
    @Column(name = "UPDATED_BY", nullable = false, length = 100) private String updatedBy;
    @OneToMany(mappedBy = "creditCardProduct", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("effectiveFrom DESC") private List<CreditCardTerms> terms = new ArrayList<>();
    @OneToMany(mappedBy = "creditCardProduct", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("effectiveFrom DESC") private List<CreditCardInterestPolicy> interestPolicies = new ArrayList<>();
    @ElementCollection @CollectionTable(name = "CREDIT_CARD_PRODUCT_FEE", joinColumns = @JoinColumn(name = "CREDIT_CARD_PRODUCT_ID"))
    private List<CatalogRuleValues.Fee> fees = new ArrayList<>();
    @ElementCollection @CollectionTable(name = "CREDIT_CARD_PRODUCT_ELIGIBILITY", joinColumns = @JoinColumn(name = "CREDIT_CARD_PRODUCT_ID"))
    private List<CatalogRuleValues.Eligibility> eligibilityRules = new ArrayList<>();
    @ElementCollection @CollectionTable(name = "CREDIT_CARD_PRODUCT_FEATURE", joinColumns = @JoinColumn(name = "CREDIT_CARD_PRODUCT_ID"))
    private List<CatalogRuleValues.Feature> features = new ArrayList<>();
    @PrePersist void onCreate() { if (id == null) id = UUID.randomUUID().toString(); Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public String getId() { return id; } public String getProductCode() { return productCode; } public void setProductCode(String value) { productCode = value; }
    public String getProductName() { return productName; } public void setProductName(String value) { productName = value; }
    public String getDescription() { return description; } public void setDescription(String value) { description = value; }
    public String getCurrencyCode() { return currencyCode; } public void setCurrencyCode(String value) { currencyCode = value; }
    public Status getStatus() { return status; } public void setStatus(Status value) { status = value; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; } public void setEffectiveFrom(LocalDate value) { effectiveFrom = value; }
    public LocalDate getEffectiveTo() { return effectiveTo; } public void setEffectiveTo(LocalDate value) { effectiveTo = value; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; } public void setCreatedBy(String value) { createdBy = value; }
    public String getUpdatedBy() { return updatedBy; } public void setUpdatedBy(String value) { updatedBy = value; }
    public List<CreditCardTerms> getTerms() { return terms; } public List<CreditCardInterestPolicy> getInterestPolicies() { return interestPolicies; }
    public List<CatalogRuleValues.Fee> getFees() { return fees; } public List<CatalogRuleValues.Eligibility> getEligibilityRules() { return eligibilityRules; }
    public List<CatalogRuleValues.Feature> getFeatures() { return features; }
}
