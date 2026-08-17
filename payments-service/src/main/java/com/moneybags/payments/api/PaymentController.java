package com.moneybags.payments.api;

import com.moneybags.payments.dto.PaymentDtos.*;
import com.moneybags.payments.service.PaymentOrchestrationService;
import com.moneybags.payments.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
@Tag(name = "Customer Payments",
    description = "Book transfer, card payments, card repayment and fixed-deposit funding")
public class PaymentController {
  private final PaymentOrchestrationService orchestration;
  private final PaymentQueryService queries;

  public PaymentController(PaymentOrchestrationService orchestration,
                           PaymentQueryService queries) {
    this.orchestration = orchestration;
    this.queries = queries;
  }

  @PostMapping("/book-transfers")
  @PreAuthorize("@paymentAuthorization.canUseCustomer(authentication, #request.requestorCustomerId())")
  @Operation(summary = "Create and settle an internal book transfer")
  @RequestBody(content = @Content(examples = @ExampleObject(value = """
      {"requestorCustomerId":101,"sourceAccountId":"dep-acc-001",
       "targetAccountId":"dep-acc-002","amount":500.00,"currencyCode":"INR",
       "reference":"Rent payment"}
      """)))
  public ResponseEntity<PaymentResponse> bookTransfer(
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String key,
      @Valid @org.springframework.web.bind.annotation.RequestBody BookTransferRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orchestration.bookTransfer(request, key, correlationId()));
  }

  @PostMapping("/credit-card-payment/merchant-payment")
  @PreAuthorize("@paymentAuthorization.canUseCustomer(authentication, #request.requestorCustomerId())")
  @Operation(summary = "Pay a merchant using a credit-card hold and capture")
  @RequestBody(content = @Content(examples = @ExampleObject(value = """
      {"requestorCustomerId":101,"creditCardAccountId":"CC-101",
       "merchantId":"MERCHANT-001","amount":50000.00,"currencyCode":"INR",
       "reference":"Merchant purchase"}
      """)))
  public ResponseEntity<PaymentResponse> merchantPayment(
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String key,
      @Valid @org.springframework.web.bind.annotation.RequestBody MerchantPaymentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orchestration.merchantPayment(request, key, correlationId()));
  }

  @PostMapping("/credit-card-payment/repayment")
  @PreAuthorize("@paymentAuthorization.canUseCustomer(authentication, #request.requestorCustomerId())")
  @Operation(summary = "Repay a generated credit-card bill from a deposit account")
  @RequestBody(content = @Content(examples = @ExampleObject(value = """
      {"requestorCustomerId":101,"billId":"BILL-202608-001",
       "sourceDepositAccountId":"dep-acc-001","creditCardAccountId":"CC-101",
       "amount":25000.00,"currencyCode":"INR","reference":"Card bill repayment"}
      """)))
  public ResponseEntity<PaymentResponse> repayment(
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String key,
      @Valid @org.springframework.web.bind.annotation.RequestBody CardRepaymentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orchestration.cardRepayment(request, key, correlationId()));
  }

  @PostMapping("/fixed-deposit-funding")
  @PreAuthorize("@paymentAuthorization.canUseCustomer(authentication, #request.requestorCustomerId())")
  @Operation(summary = "Fund and activate a fixed deposit from a deposit account")
  @RequestBody(content = @Content(examples = @ExampleObject(value = """
      {"requestorCustomerId":101,"sourceAccountId":"dep-acc-001",
       "fixedDepositId":"fd-001","amount":100000.00,"currencyCode":"INR",
       "reference":"Initial funding for fd-001"}
      """)))
  public ResponseEntity<PaymentResponse> fixedDepositFunding(
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String key,
      @Valid @org.springframework.web.bind.annotation.RequestBody
      FixedDepositFundingRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(orchestration.fixedDepositFunding(request, key, correlationId()));
  }

  @GetMapping("/{paymentId}")
  @PreAuthorize("@paymentAuthorization.canAccessPayment(authentication, #paymentId)")
  @Operation(summary = "Get a payment by ID")
  public PaymentResponse get(@PathVariable String paymentId) {
    return queries.get(paymentId);
  }

  @GetMapping("/{paymentId}/history")
  @PreAuthorize("@paymentAuthorization.canAccessPayment(authentication, #paymentId)")
  @Operation(summary = "Get the ordered lifecycle history for a payment")
  public List<PaymentStatusHistoryResponse> history(@PathVariable String paymentId) {
    return queries.history(paymentId);
  }

  @GetMapping
  @PreAuthorize("@paymentAuthorization.canUseCustomer(authentication, #customerId)")
  @Operation(summary = "List a customer's payments, newest first")
  public PageResponse<PaymentResponse> byCustomer(
      @RequestParam @NotNull @Positive Long customerId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
    return queries.byCustomer(customerId, page, size);
  }

  @PostMapping("/{paymentId}/cancel")
  @PreAuthorize("@paymentAuthorization.canAccessPayment(authentication, #paymentId)")
  @Operation(summary = "Cancel a payment that has not posted to Accounting")
  public PaymentResponse cancel(@PathVariable String paymentId) {
    return queries.cancel(paymentId);
  }

  private String correlationId() {
    return MDC.get("correlationId");
  }
}
