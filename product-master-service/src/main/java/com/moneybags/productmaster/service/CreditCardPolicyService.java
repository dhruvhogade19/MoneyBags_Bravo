package com.moneybags.productmaster.service;

import com.moneybags.productmaster.api.ProductDtos.CreditCardRuleDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreditCardPolicyService {
    private final ProductService products;
    public CreditCardPolicyService(ProductService products) { this.products = products; }
    public CreditCardRuleDto addPolicy(String productCode, CreditCardRuleDto request) { return products.addCreditCardTerms(productCode, request); }
    @Transactional(readOnly = true) public List<CreditCardRuleDto> policies(String productCode) { return products.creditCardTerms(productCode); }
}
