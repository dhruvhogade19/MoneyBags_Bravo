package com.moneybags.deposit.fixeddeposit.controller;

import com.moneybags.deposit.fixeddeposit.dto.FixedDepositRequests.EodRequest;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositResponses.*;
import com.moneybags.deposit.fixeddeposit.service.FixedDepositEodService;
import com.moneybags.deposit.service.IdempotentMutationExecutor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/deposit-accounts/eod")
public class FixedDepositEodController {
    // Scope versioning lets a retry bypass COMPLETED responses cached before Accounting posting recovery existed.
    private static final String ACCRUAL_SCOPE = "FD_EOD_ACCRUAL_ACCOUNTING_V3";
    private static final String MATURITY_SCOPE = "FD_EOD_MATURITY_ACCOUNTING_V3";
    private final FixedDepositEodService service; private final IdempotentMutationExecutor idempotency;
    public FixedDepositEodController(FixedDepositEodService service,IdempotentMutationExecutor idempotency){this.service=service;this.idempotency=idempotency;}
    @PostMapping("/fixed-deposit-accruals") public EodResult accrue(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody EodRequest request){
        return idempotency.execute(ACCRUAL_SCOPE,key,request,EodResult.class,()->service.accrue(request));
    }
    @PostMapping("/fixed-deposit-maturities") public EodResult mature(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody EodRequest request){
        return idempotency.execute(MATURITY_SCOPE,key,request,EodResult.class,()->service.mature(request));
    }
    @GetMapping("/fixed-deposit-readiness") public ReadinessResponse readiness(){return service.readiness();}
}
