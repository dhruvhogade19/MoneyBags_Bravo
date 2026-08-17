package com.moneybags.deposit.controller;

import com.moneybags.deposit.dto.AccountResponses.AccountEligibilityView;
import com.moneybags.deposit.service.DepositAccountApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/deposit-accounts")
public class InternalDepositAccountController {
    private final DepositAccountApplicationService service;

    public InternalDepositAccountController(DepositAccountApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{accountId}/eligibility")
    public AccountEligibilityView eligibility(@PathVariable String accountId) {
        return service.internalEligibility(accountId);
    }
}
