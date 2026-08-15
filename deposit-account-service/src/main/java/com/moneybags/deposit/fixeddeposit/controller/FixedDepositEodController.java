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
    private final FixedDepositEodService service; private final IdempotentMutationExecutor idempotency;
    public FixedDepositEodController(FixedDepositEodService service,IdempotentMutationExecutor idempotency){this.service=service;this.idempotency=idempotency;}
    @PostMapping("/fixed-deposit-accruals") public EodResult accrue(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody EodRequest request){
        return idempotency.execute("FD_EOD_ACCRUAL",key,request,EodResult.class,()->service.accrue(request));
    }
    @PostMapping("/fixed-deposit-maturities") public EodResult mature(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody EodRequest request){
        return idempotency.execute("FD_EOD_MATURITY",key,request,EodResult.class,()->service.mature(request));
    }
    @GetMapping("/fixed-deposit-readiness") public ReadinessResponse readiness(){return service.readiness();}
}
