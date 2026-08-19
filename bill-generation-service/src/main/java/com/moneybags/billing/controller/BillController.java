package com.moneybags.billing.controller;

import com.moneybags.billing.BillGenerationApplication.ApiException;
import com.moneybags.billing.BillGenerationApplication.AdminStatementRequest;
import com.moneybags.billing.BillGenerationApplication.BillPage;
import com.moneybags.billing.BillGenerationApplication.BillResponse;
import com.moneybags.billing.BillGenerationApplication.BillSummaryResponse;
import com.moneybags.billing.BillGenerationApplication.BillingService;
import com.moneybags.billing.BillGenerationApplication.CloseRequest;
import com.moneybags.billing.BillGenerationApplication.CloseResponse;
import com.moneybags.billing.BillGenerationApplication.ClosureEligibilityResponse;
import com.moneybags.billing.BillGenerationApplication.GenerateRequest;
import com.moneybags.billing.BillGenerationApplication.PaymentSettlementRequest;
import com.moneybags.billing.BillGenerationApplication.CustomerStatementRequest;
import com.moneybags.billing.BillGenerationApplication.StatementPreview;
import com.moneybags.billing.StatementPdfRenderer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping
public class BillController {
    private final BillingService service;
    private final StatementPdfRenderer pdfRenderer;

    public BillController(BillingService service, StatementPdfRenderer pdfRenderer) {
        this.service = service;
        this.pdfRenderer = pdfRenderer;
    }

    @GetMapping("/")
    Map<String, Object> home() {
        return Map.of(
                "service", "bill-generation-service", "status", "UP",
                "documentation", "/swagger-ui.html", "health", "/actuator/health",
                "generateBill", "POST /internal/v1/bills/generate",
                "findBills", "GET /api/v1/bills", "getBill", "GET /api/v1/bills/{billId}");
    }

    @PostMapping("/internal/v1/bills/generate")
    BillResponse generate(@RequestHeader("Idempotency-Key") @NotBlank String key,
                          @Valid @RequestBody GenerateRequest request) {
        return service.generate(key, request);
    }

    @PostMapping("/api/v1/bills/preview")
    StatementPreview preview(@RequestHeader(name = "X-Customer-ID", required = false) String gatewayCustomerId,
                             @AuthenticationPrincipal Jwt jwt,
                             @Valid @RequestBody CustomerStatementRequest request) {
        Long customerId = requireCustomerId(jwt, gatewayCustomerId);
        return service.previewForCustomer(customerId, request);
    }

    @PostMapping("/api/v1/bills")
    BillResponse generateForCustomer(@RequestHeader("Idempotency-Key") @NotBlank String key,
                                     @RequestHeader(name = "X-Customer-ID", required = false) String gatewayCustomerId,
                                     @AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody CustomerStatementRequest request) {
        Long customerId = requireCustomerId(jwt, gatewayCustomerId);
        return service.generateForCustomer(key, customerId, request);
    }

    @PostMapping("/api/v1/bills/admin/preview")
    StatementPreview previewForAdmin(@Valid @RequestBody AdminStatementRequest request) {
        return service.previewForAdmin(request);
    }

    @PostMapping("/api/v1/bills/admin")
    BillResponse generateForAdmin(@RequestHeader("Idempotency-Key") @NotBlank String key,
                                  @Valid @RequestBody AdminStatementRequest request) {
        return service.generateForAdmin(key, request);
    }

    @GetMapping("/api/v1/bills/{billId}")
    BillResponse get(@PathVariable String billId,
                     @RequestHeader(name = "X-Customer-ID", required = false) String gatewayCustomerId,
                     @AuthenticationPrincipal Jwt jwt) {
        Long customerId = customerId(jwt, gatewayCustomerId);
        return customerId == null ? service.get(billId) : service.getForCustomer(billId, customerId);
    }

    @GetMapping(value = "/api/v1/bills/{billId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> pdf(@PathVariable String billId,
                               @RequestParam(defaultValue = "inline") String disposition,
                               @RequestHeader(name = "X-Customer-ID", required = false) String gatewayCustomerId,
                               @AuthenticationPrincipal Jwt jwt) {
        Long customerId = customerId(jwt, gatewayCustomerId);
        BillResponse bill = customerId == null ? service.get(billId) : service.getForCustomer(billId, customerId);
        String mode = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, mode + "; filename=MoneyBags-" + bill.billId() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfRenderer.render(bill));
    }

    @GetMapping("/api/v1/bills")
    BillPage customerSearch(@RequestHeader(name = "X-Customer-ID", required = false) String gatewayCustomerId,
                            @AuthenticationPrincipal Jwt jwt,
                            @RequestParam(required = false) String accountId,
                            @RequestParam(required = false) String billingPeriod,
                            @RequestParam(required = false) String status,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        Long customerId = customerId(jwt, gatewayCustomerId);
        return customerId == null
                ? service.search(accountId, billingPeriod, status, page, size)
                : service.searchForCustomer(customerId, accountId, billingPeriod, status, page, size);
    }

    @GetMapping("/internal/v1/bills/{billId}")
    BillResponse internalGet(@PathVariable String billId) {
        return service.get(billId);
    }

    @GetMapping("/internal/v1/bills")
    BillPage search(@RequestParam(required = false) String accountId,
                    @RequestParam(required = false) String billingPeriod,
                    @RequestParam(required = false) String status,
                    @RequestParam(defaultValue = "0") int page,
                    @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return service.search(accountId, billingPeriod, status, page, size);
    }

    @GetMapping("/internal/v1/bills/{billId}/summary")
    BillSummaryResponse summary(@PathVariable String billId) {
        BillResponse bill = service.get(billId);
        return new BillSummaryResponse(bill.billId(), bill.accountId(), bill.billingPeriod(), bill.totalAmountDue(),
                bill.minimumAmountDue(), bill.paidAmount(), bill.outstandingAmount(), bill.status(), bill.paymentDueDate());
    }

    @PostMapping("/internal/v1/bills/{billId}/payment-settlements")
    BillSummaryResponse settlePayment(@PathVariable String billId, @Valid @RequestBody PaymentSettlementRequest request) {
        return service.settlePayment(billId, request);
    }

    @GetMapping("/internal/v1/bills/accounts/{accountId}/closure-eligibility")
    ClosureEligibilityResponse closureEligibility(@PathVariable String accountId) {
        return service.closureEligibility(accountId);
    }

    @PostMapping("/internal/v1/bills/eod/close")
    CloseResponse close(@RequestHeader("Idempotency-Key") @NotBlank String ignored,
                        @Valid @RequestBody CloseRequest request) {
        return service.close(request);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "page must be >= 0 and size must be 1..100");
        }
    }

    private static Long customerId(Jwt jwt, String gatewayCustomerId) {
        if (jwt != null) {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null && roles.contains("BANK_ADMIN")) return null;
            Object claim = jwt.getClaim("customer_id");
            if (claim == null) throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_CONTEXT_REQUIRED", "Customer identity is required");
            long authenticated = Long.parseLong(claim.toString());
            if (gatewayCustomerId != null && authenticated != Long.parseLong(gatewayCustomerId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_CONTEXT_MISMATCH", "Customer identity does not match the gateway context");
            }
            return authenticated;
        }
        if (gatewayCustomerId == null || gatewayCustomerId.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_CONTEXT_REQUIRED", "X-Customer-ID is required");
        }
        try {
            return Long.parseLong(gatewayCustomerId);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CUSTOMER_CONTEXT", "X-Customer-ID must be numeric");
        }
    }

    private static Long requireCustomerId(Jwt jwt, String gatewayCustomerId) {
        Long value = customerId(jwt, gatewayCustomerId);
        if (value == null)
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_CONTEXT_REQUIRED", "A customer session is required to generate a statement");
        return value;
    }
}
