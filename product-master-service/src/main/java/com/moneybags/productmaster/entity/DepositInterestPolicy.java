package com.moneybags.productmaster.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "DEPOSIT_INTEREST_POLICY")
public class DepositInterestPolicy extends AbstractInterestPolicy {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "DEPOSIT_PRODUCT_ID", nullable = false)
    private DepositProduct depositProduct;
    public DepositProduct getDepositProduct() { return depositProduct; }
    public void setDepositProduct(DepositProduct value) { depositProduct = value; }
}
