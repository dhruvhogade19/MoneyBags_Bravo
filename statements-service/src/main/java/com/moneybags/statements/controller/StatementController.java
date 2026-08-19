package com.moneybags.statements.controller;

import com.moneybags.statements.api.StatementDtos.AccountActivityView;
import com.moneybags.statements.api.StatementDtos.GenerateAccountStatementRequest;
import com.moneybags.statements.api.StatementDtos.GenerateStatementRequest;
import com.moneybags.statements.api.StatementDtos.StatementView;
import com.moneybags.statements.service.StatementService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatementController {
    private final StatementService service;

    public StatementController(StatementService service) { this.service = service; }

    @GetMapping("/api/v1/statements/accounts/{accountReference}/activity")
    AccountActivityView activity(
            @PathVariable String accountReference,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        return service.activity(accountReference, from, to, customer(authentication),
                privileged(authentication));
    }

    @PostMapping("/api/v1/statements")
    ResponseEntity<StatementView> generateForAccount(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody GenerateAccountStatementRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generateForAccount(request,
                customer(authentication), privileged(authentication)));
    }

    @PostMapping("/internal/v1/statements/generate")
    ResponseEntity<StatementView> generate(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody GenerateStatementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generate(request));
    }

    @GetMapping("/api/v1/statements/{statementId}")
    StatementView get(@PathVariable String statementId, Authentication authentication) {
        return service.get(statementId, customer(authentication), privileged(authentication));
    }

    @GetMapping("/api/v1/statements/{statementId}/download")
    ResponseEntity<byte[]> download(@PathVariable String statementId,
                                    Authentication authentication) {
        var statement = service.document(statementId, customer(authentication),
                privileged(authentication));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .eTag("\"" + statement.getDocumentSha256() + "\"")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("statement-" + statement.getId() + ".pdf").build().toString())
                .body(statement.getDocumentData());
    }

    private String customer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) return null;
        return jwt.getClaimAsString("customer_id");
    }

    private boolean privileged(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_BANK_ADMIN")
                        || authority.getAuthority().equals("SCOPE_statements:admin"));
    }
}
