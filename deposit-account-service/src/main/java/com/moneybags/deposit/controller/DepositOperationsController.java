package com.moneybags.deposit.controller;

import com.moneybags.deposit.dto.EodRequests.DepositAccrualRequest;
import com.moneybags.deposit.dto.EodResponses.DepositAccrualResponse;
import com.moneybags.deposit.dto.EodResponses.ServiceReadinessResponse;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositRequests.EodRequest;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositResponses.EodResult;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositResponses.ReadinessResponse;
import com.moneybags.deposit.fixeddeposit.service.FixedDepositEodService;
import com.moneybags.deposit.service.DepositEodService;
import com.moneybags.deposit.service.IdempotentMutationExecutor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deposit-accounts/operations/eod")
@PreAuthorize("hasAnyAuthority('SCOPE_account:admin','SCOPE_fd:admin')")
public class DepositOperationsController {
    private final DepositEodService deposits;
    private final FixedDepositEodService fixedDeposits;
    private final IdempotentMutationExecutor idempotency;

    public DepositOperationsController(DepositEodService deposits,
                                       FixedDepositEodService fixedDeposits,
                                       IdempotentMutationExecutor idempotency) {
        this.deposits = deposits;
        this.fixedDeposits = fixedDeposits;
        this.idempotency = idempotency;
    }

    @GetMapping("/readiness")
    public OperationsReadiness readiness() {
        return operationsReadiness(deposits, fixedDeposits);
    }

    static OperationsReadiness operationsReadiness(DepositEodService deposits,
                                                   FixedDepositEodService fixedDeposits) {
        ServiceReadinessResponse depositReadiness = deposits.readiness();
        ReadinessResponse fixedDepositReadiness = fixedDeposits.readiness();
        List<String> blockers = java.util.stream.Stream.concat(
                depositReadiness.blockers().stream(), fixedDepositReadiness.blockers().stream()).toList();
        return new OperationsReadiness(depositReadiness.ready() && fixedDepositReadiness.ready(), blockers,
                depositReadiness, fixedDepositReadiness);
    }

    @PostMapping("/account-accruals")
    public DepositAccrualResponse accountAccruals(@Valid @RequestBody DepositAccrualRequest request) {
        return deposits.runAccruals(request);
    }

    @PostMapping("/fixed-deposit-accruals")
    public EodResult fixedDepositAccruals(@RequestHeader("Idempotency-Key") String key,
                                          @Valid @RequestBody EodRequest request) {
        return idempotency.execute("FD_ADMIN_EOD_ACCRUAL_ACCOUNTING_V3", key, request, EodResult.class,
                () -> fixedDeposits.accrue(request));
    }

    @PostMapping("/fixed-deposit-maturities")
    public EodResult fixedDepositMaturities(@RequestHeader("Idempotency-Key") String key,
                                             @Valid @RequestBody EodRequest request) {
        return idempotency.execute("FD_ADMIN_EOD_MATURITY_ACCOUNTING_V3", key, request, EodResult.class,
                () -> fixedDeposits.mature(request));
    }

    public record OperationsReadiness(boolean ready, List<String> blockers,
                                      ServiceReadinessResponse depositAccounts,
                                      ReadinessResponse fixedDeposits) {}
}
