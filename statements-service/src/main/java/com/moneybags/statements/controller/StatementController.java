package com.moneybags.statements.controller;

import com.moneybags.statements.api.StatementDtos.*;
import com.moneybags.statements.service.StatementService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class StatementController {
    private final StatementService service; public StatementController(StatementService service) { this.service = service; }
    @PostMapping("/internal/v1/statements/generate")
    ResponseEntity<StatementView> generate(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody GenerateStatementRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.generate(request)); }
    @GetMapping("/api/v1/statements/{statementId}") StatementView get(@PathVariable String statementId, Authentication authentication) { return service.get(statementId, cif(authentication), privileged(authentication)); }
    @GetMapping("/api/v1/statements/{statementId}/download") ResponseEntity<byte[]> download(@PathVariable String statementId, Authentication authentication) {
        var statement = service.document(statementId, cif(authentication), privileged(authentication));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).eTag("\"" + statement.getDocumentSha256() + "\"").header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("statement-" + statement.getId() + ".pdf").build().toString()).body(statement.getDocumentData());
    }
    private String cif(Authentication auth) { if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) return null; String value = jwt.getClaimAsString("cifId"); return value == null ? jwt.getClaimAsString("cif_id") : value; }
    private boolean privileged(Authentication auth) { return auth == null || auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_BANK_ADMIN") || a.getAuthority().equals("SCOPE_statements:admin")); }
}
