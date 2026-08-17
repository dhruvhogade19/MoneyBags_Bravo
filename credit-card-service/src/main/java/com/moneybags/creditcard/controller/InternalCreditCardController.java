package com.moneybags.creditcard.controller;

import com.moneybags.creditcard.dto.CreditCardDtos.BillingAccountDetails;
import com.moneybags.creditcard.dto.CreditCardDtos.EodReadinessResponse;
import com.moneybags.creditcard.service.CreditCardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/credit-card-accounts")
public class InternalCreditCardController {
    private final CreditCardService service;
    public InternalCreditCardController(CreditCardService service) { this.service = service; }

    @GetMapping("/{accountId}/billing-details")
    BillingAccountDetails billingDetails(@PathVariable Long accountId) {
        return service.billingDetails(accountId);
    }

    @GetMapping("/eod/readiness")
    EodReadinessResponse eodReadiness() { return service.eod(); }
}
