package com.moneybags.deposit.controller;

import com.moneybags.deposit.dto.EodRequests.DepositAccrualRequest;
import com.moneybags.deposit.dto.EodResponses.DepositAccrualResponse;
import com.moneybags.deposit.dto.EodResponses.ServiceReadinessResponse;
import com.moneybags.deposit.fixeddeposit.service.FixedDepositEodService;
import com.moneybags.deposit.service.DepositEodService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/deposit-accounts/eod")
public class DepositEodController {
    private final DepositEodService service;
    private final FixedDepositEodService fixedDeposits;

    public DepositEodController(DepositEodService service, FixedDepositEodService fixedDeposits) {
        this.service = service;
        this.fixedDeposits = fixedDeposits;
    }

    @PostMapping("/accruals")
    public DepositAccrualResponse accruals(@Valid @RequestBody DepositAccrualRequest request) {
        return service.runAccruals(request);
    }

    @GetMapping("/readiness")
    public ServiceReadinessResponse readiness() { return service.readiness(); }

    /** Composite internal alias for service-to-service EOD orchestration. */
    @GetMapping("/operations-readiness")
    public DepositOperationsController.OperationsReadiness operationsReadiness() {
        return DepositOperationsController.operationsReadiness(service, fixedDeposits);
    }
}
