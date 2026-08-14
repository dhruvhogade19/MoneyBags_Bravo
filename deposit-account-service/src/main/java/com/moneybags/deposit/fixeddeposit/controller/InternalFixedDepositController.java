package com.moneybags.deposit.fixeddeposit.controller;

import com.moneybags.deposit.fixeddeposit.dto.FixedDepositResponses.FixedDepositView;
import com.moneybags.deposit.fixeddeposit.service.FixedDepositApplicationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/deposit-accounts/fixed-deposits")
public class InternalFixedDepositController {
    private final FixedDepositApplicationService service;
    public InternalFixedDepositController(FixedDepositApplicationService service){this.service=service;}
    @GetMapping("/{fdId}") public FixedDepositView get(@PathVariable String fdId){return service.get(fdId);}
}
