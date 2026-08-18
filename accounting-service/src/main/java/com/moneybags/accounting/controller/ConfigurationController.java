package com.moneybags.accounting.controller;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.exception.ApiException;
import com.moneybags.accounting.service.ConfigurationService;
import com.moneybags.accounting.domain.DomainTypes.GlAccountType;
import com.moneybags.accounting.domain.DomainTypes.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ConfigurationController {
    private final ConfigurationService configuration;
    public ConfigurationController(ConfigurationService configuration) { this.configuration = configuration; }

    @PostMapping("/gl-accounts")
    ResponseEntity<GlAccountResponse> createGl(@Valid @RequestBody GlAccountRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Actor-Id") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configuration.createGl(request, key, actor));
    }

    @GetMapping("/gl-accounts")
    GlAccountPage glAccounts(@RequestParam(required = false) String search,
                             @RequestParam(required = false) GlAccountType accountType,
                             @RequestParam(required = false) RecordStatus status,
                             @RequestParam(required = false) @Pattern(regexp = "[A-Z]{3}") String currencyCode,
                             @RequestParam(defaultValue = "0") @Min(0) int page,
                             @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return configuration.listGl(search, accountType, status, currencyCode, page, size);
    }

    @GetMapping("/gl-accounts/{glCode}")
    ResponseEntity<GlAccountResponse> glAccount(@PathVariable String glCode) {
        GlAccountResponse value = configuration.getGl(glCode);
        return ResponseEntity.ok().eTag(Long.toString(value.version())).body(value);
    }

    @PatchMapping("/gl-accounts/{glCode}/status")
    ResponseEntity<GlAccountResponse> status(@PathVariable String glCode,
            @Valid @RequestBody StatusChangeRequest request,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Actor-Id") String actor) {
        GlAccountResponse value = configuration.changeStatus(glCode, request, parseVersion(ifMatch), key, actor);
        return ResponseEntity.ok().eTag(Long.toString(value.version())).body(value);
    }

    @PostMapping("/accounting-rules")
    ResponseEntity<AccountingRuleResponse> createRule(@Valid @RequestBody AccountingRuleRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Actor-Id") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configuration.createRule(request, key, actor));
    }

    @GetMapping("/accounting-rules")
    AccountingRulePage rules(@RequestParam(required = false) String search,
                             @RequestParam(required = false) String eventType,
                             @RequestParam(required = false) RecordStatus status,
                             @RequestParam(required = false) @Pattern(regexp = "[A-Z]{3}") String currencyCode,
                             @RequestParam(defaultValue = "0") @Min(0) int page,
                             @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return configuration.listRules(search, eventType, status, currencyCode, page, size);
    }

    @PostMapping("/subledger-mappings")
    ResponseEntity<SubledgerMappingResponse> createMapping(@Valid @RequestBody SubledgerMappingRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 160) String key,
            @RequestHeader("X-Actor-Id") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configuration.createMapping(request, key, actor));
    }

    @GetMapping("/subledger-mappings")
    SubledgerMappingPage mappings(@RequestParam(required = false) String search,
                                  @RequestParam(required = false) String glCode,
                                  @RequestParam(required = false) RecordStatus status,
                                  @RequestParam(required = false) @Pattern(regexp = "[A-Z]{3}") String currencyCode,
                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                  @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return configuration.listMappings(search, glCode, status, currencyCode, page, size);
    }

    private long parseVersion(String value) {
        try { return Long.parseLong(value.replace("W/", "").replace("\"", "").trim()); }
        catch (NumberFormatException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IF_MATCH",
                "If-Match must contain the numeric resource version"); }
    }
}
