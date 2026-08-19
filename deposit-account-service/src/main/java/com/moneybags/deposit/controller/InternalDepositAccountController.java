package com.moneybags.deposit.controller;

import com.moneybags.deposit.dto.AccountResponses.AccountEligibilityView;
import com.moneybags.deposit.dto.StatementResponses.StatementAccountContext;
import com.moneybags.deposit.dto.StatementResponses.StatementActivityPage;
import com.moneybags.deposit.service.DepositAccountApplicationService;
import com.moneybags.deposit.service.StatementActivityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/deposit-accounts")
public class InternalDepositAccountController {
    private final DepositAccountApplicationService service;
    private final StatementActivityService statements;

    public InternalDepositAccountController(DepositAccountApplicationService service,
                                            StatementActivityService statements) {
        this.service = service;
        this.statements = statements;
    }

    @GetMapping("/{accountId}/eligibility")
    public AccountEligibilityView eligibility(@PathVariable String accountId) {
        return service.internalEligibility(accountId);
    }

    @GetMapping("/{accountId}/statement-context")
    public StatementAccountContext statementContext(@PathVariable String accountId) {
        return statements.context(accountId);
    }

    @GetMapping("/{accountId}/statement-activities")
    public StatementActivityPage statementActivities(
            @PathVariable String accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
        return statements.activities(accountId, from, to, page, size);
    }
}
