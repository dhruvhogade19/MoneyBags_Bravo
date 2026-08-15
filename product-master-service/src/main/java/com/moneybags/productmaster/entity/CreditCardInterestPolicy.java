package com.moneybags.productmaster.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "CREDIT_CARD_INTEREST_POLICY")
public class CreditCardInterestPolicy extends AbstractInterestPolicy {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "CREDIT_CARD_PRODUCT_ID", nullable = false)
    private CreditCardProduct creditCardProduct;
    public CreditCardProduct getCreditCardProduct() { return creditCardProduct; }
    public void setCreditCardProduct(CreditCardProduct value) { creditCardProduct = value; }
}
