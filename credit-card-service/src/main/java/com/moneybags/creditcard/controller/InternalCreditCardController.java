package com.moneybags.creditcard.controller;

import com.moneybags.creditcard.dto.CreditCardDtos.BillingAccountDetails;
import com.moneybags.creditcard.dto.CreditCardDtos.BillingChargeRequest;
import com.moneybags.creditcard.dto.CreditCardDtos.BillingChargeResponse;
import jakarta.validation.Valid;
import com.moneybags.creditcard.service.CreditCardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/internal/v1/credit-card-accounts")
public class InternalCreditCardController {
    private final CreditCardService service;
    public InternalCreditCardController(CreditCardService service) { this.service = service; }

    @GetMapping("/{accountId}/billing-details")
    BillingAccountDetails billingDetails(@PathVariable Long accountId) {
        return service.billingDetails(accountId);
    }

    @PostMapping("/{accountId}/billing-charges")
    BillingChargeResponse applyBillingCharges(@PathVariable Long accountId,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @Valid @RequestBody BillingChargeRequest request) {
        return service.applyBillingCharges(accountId, idempotencyKey, request);
    }
}
