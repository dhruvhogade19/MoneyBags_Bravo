package com.moneybags.deposit.controller;

import com.moneybags.deposit.dto.EodRequests.DepositAccrualRequest;
import com.moneybags.deposit.dto.EodResponses.DepositAccrualResponse;
import com.moneybags.deposit.dto.EodResponses.ServiceReadinessResponse;
import com.moneybags.deposit.service.DepositEodService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/deposit-accounts/eod")
public class DepositEodController {
    private final DepositEodService service;

    public DepositEodController(DepositEodService service) { this.service = service; }

    @PostMapping("/accruals")
    public DepositAccrualResponse accruals(@Valid @RequestBody DepositAccrualRequest request) {
        return service.runAccruals(request);
    }

    @GetMapping("/readiness")
    public ServiceReadinessResponse readiness() { return service.readiness(); }
}
