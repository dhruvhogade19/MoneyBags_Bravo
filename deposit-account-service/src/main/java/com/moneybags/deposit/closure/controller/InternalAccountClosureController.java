package com.moneybags.deposit.closure.controller;

import com.moneybags.deposit.closure.dto.AccountClosureResponses.ClosureRequestView;
import com.moneybags.deposit.closure.service.CasaClosureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/deposit-accounts/closures")
public class InternalAccountClosureController {
    private final CasaClosureService service;

    public InternalAccountClosureController(CasaClosureService service) {
        this.service = service;
    }

    @GetMapping("/{requestId}")
    public ClosureRequestView get(@PathVariable String requestId) {
        return service.getById(requestId);
    }
}
