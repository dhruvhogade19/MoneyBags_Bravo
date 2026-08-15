package com.moneybags.deposit.fixeddeposit.controller;

import com.moneybags.deposit.domain.DomainTypes.FixedDepositStatus;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositRequests.*;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositResponses.*;
import com.moneybags.deposit.fixeddeposit.service.*;
import com.moneybags.deposit.service.IdempotentMutationExecutor;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/deposit-accounts/fixed-deposits")
public class FixedDepositController {
    private final FixedDepositQuoteService quotes; private final FixedDepositApplicationService service;
    private final IdempotentMutationExecutor idempotency;
    public FixedDepositController(FixedDepositQuoteService quotes, FixedDepositApplicationService service,
                                  IdempotentMutationExecutor idempotency){this.quotes=quotes;this.service=service;this.idempotency=idempotency;}
    @PostMapping("/quotes") public QuoteResponse quote(@Valid @RequestBody QuoteRequest request){return quotes.quote(request);}
    @PostMapping public ResponseEntity<FixedDepositView> book(@RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody BookingRequest request, Authentication authentication){
        FixedDepositView result=idempotency.execute("BOOK_FIXED_DEPOSIT",key,request,FixedDepositView.class,
            ()->service.book(request,"FD-"+key,actor(authentication),correlationId()));
        return ResponseEntity.created(URI.create("/api/deposit-accounts/fixed-deposits/"+result.fixedDepositId())).body(result);
    }
    @GetMapping("/{fdId}") public FixedDepositView get(@PathVariable String fdId){return service.get(fdId);}
    @GetMapping public Page<FixedDepositView> search(@RequestParam(required=false) String customerId,
        @RequestParam(required=false) FixedDepositStatus status, @RequestParam(required=false) LocalDate maturingBefore,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return service.search(customerId,status,maturingBefore,PageRequest.of(Math.max(0,page),Math.min(100,Math.max(1,size)),Sort.by(Sort.Direction.DESC,"createdAt")));
    }
    @GetMapping("/{fdId}/interest-accruals") public List<AccrualView> accruals(@PathVariable String fdId){return service.accruals(fdId);}
    @GetMapping("/{fdId}/projected-schedule") public ProjectedScheduleResponse projectedSchedule(@PathVariable String fdId){return service.projectedSchedule(fdId);}
    private String actor(Authentication a){return Optional.ofNullable(a).map(Authentication::getName).orElse("local-user");}
    private String correlationId(){return Optional.ofNullable(MDC.get("correlationId")).orElse("missing-correlation-id");}
}
