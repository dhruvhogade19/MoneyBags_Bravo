package com.moneybags.billing.controller;

import com.moneybags.billing.BillGenerationApplication.ApiException;
import com.moneybags.billing.BillGenerationApplication.BillPage;
import com.moneybags.billing.BillGenerationApplication.BillResponse;
import com.moneybags.billing.BillGenerationApplication.BillSummaryResponse;
import com.moneybags.billing.BillGenerationApplication.BillingService;
import com.moneybags.billing.BillGenerationApplication.CloseRequest;
import com.moneybags.billing.BillGenerationApplication.CloseResponse;
import com.moneybags.billing.BillGenerationApplication.ClosureEligibilityResponse;
import com.moneybags.billing.BillGenerationApplication.GenerateRequest;
import com.moneybags.billing.BillGenerationApplication.PaymentSettlementRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public class BillController {
    private final BillingService service;

    public BillController(BillingService service) {
        this.service = service;
    }

    @GetMapping("/")
    Map<String, Object> home() {
        return Map.of(
                "service", "bill-generation-service", "status", "UP",
                "documentation", "/swagger-ui.html", "health", "/actuator/health",
                "generateBill", "POST /internal/v1/bills/generate",
                "findBills", "GET /internal/v1/bills", "getBill", "GET /api/v1/bills/{billId}");
    }

    @PostMapping("/internal/v1/bills/generate")
    BillResponse generate(@RequestHeader("Idempotency-Key") @NotBlank String key,
                          @Valid @RequestBody GenerateRequest request) {
        return service.generate(key, request);
    }

    @GetMapping("/api/v1/bills/{billId}")
    BillResponse get(@PathVariable String billId) {
        return service.get(billId);
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
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "page must be >= 0 and size must be 1..100");
        }
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
}
