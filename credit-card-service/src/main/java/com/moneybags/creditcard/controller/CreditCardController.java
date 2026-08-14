package com.moneybags.creditcard.controller;

import com.moneybags.creditcard.dto.CreditCardDtos.*;
import com.moneybags.creditcard.service.CreditCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-cards")
public class CreditCardController {
    private static final String ERROR_SCHEMA = "Error response: { message: string }.";
    private final CreditCardService service;

    public CreditCardController(CreditCardService service) {
        this.service = service;
    }

    @Operation(tags = "Customer / Admin - Applications", summary = "Submit a credit-card application",
            description = "Intended customer/channel API; authentication is not currently enforced. CIF ID, product code, and requested credit limit are supplied by the client. Eligibility is validated through CIF and Product Master; eligible applications are approved and create an account automatically.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Application decision recorded", content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = ERROR_SCHEMA), @ApiResponse(responseCode = "404", description = "CIF or referenced resource not found"), @ApiResponse(responseCode = "409", description = ERROR_SCHEMA)})
    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationResponse submit(@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Application fields.", content = @Content(schema = @Schema(implementation = ApplicationRequest.class)))
                               @Valid @RequestBody ApplicationRequest r) {
        return service.submit(r);
    }

    @Operation(tags = "Customer / Admin - Applications", summary = "Get a credit-card application", description = "Intended customer/admin read API; authentication is not currently enforced.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Application found", content = @Content(schema = @Schema(implementation = ApplicationResponse.class))), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA)})
    @GetMapping("/applications/{applicationId}")
    ApplicationResponse application(@Parameter(description = "Credit-card application ID.", required = true, example = "1001") @PathVariable("applicationId") Long applicationId) {
        return service.application(applicationId);
    }

    @Operation(tags = "Customer / Admin - Applications", summary = "List applications by CIF", description = "Intended customer/admin read API; authentication is not currently enforced.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Applications for the CIF", content = @Content(schema = @Schema(implementation = ApplicationResponse.class))), @ApiResponse(responseCode = "400", description = ERROR_SCHEMA)})
    @GetMapping("/applications/cif/{cifId}")
    List<ApplicationResponse> applications(@Parameter(description = "Customer CIF ID.", required = true, example = "101") @PathVariable Long cifId) {
        return service.applications(cifId);
    }

    @Operation(tags = "Customer / Admin - Applications", summary = "Approve an application", description = "Intended admin-only operation; authentication/roles are not currently enforced. A pending eligible application receives its requested limit and creates an account.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Application approved and account created", content = @Content(schema = @Schema(implementation = AccountResponse.class))), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA), @ApiResponse(responseCode = "409", description = "Application is not pending or is ineligible")})
    @PostMapping("/applications/{applicationId}/approve")
    AccountResponse approve(@Parameter(description = "Credit-card application ID.", required = true, example = "1001") @PathVariable("applicationId") Long applicationId) {
        return service.approve(applicationId);
    }

    @Operation(tags = "Customer / Admin - Applications", summary = "Reject an application", description = "Intended admin-only operation; authentication/roles are not currently enforced. Only pending applications can be rejected.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Application rejected", content = @Content(schema = @Schema(implementation = ApplicationResponse.class))), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA), @ApiResponse(responseCode = "409", description = "Application is not pending")})
    @PostMapping("/applications/{applicationId}/reject")
    ApplicationResponse reject(@Parameter(description = "Credit-card application ID.", required = true, example = "1001") @PathVariable("applicationId") Long applicationId) {
        return service.reject(applicationId);
    }

    @Operation(tags = "Customer / Admin - Accounts", summary = "Create an account from an approved application", description = "Intended admin/system operation; authentication is not currently enforced. The approved application must not already have an account.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Account created", content = @Content(schema = @Schema(implementation = AccountResponse.class))), @ApiResponse(responseCode = "400", description = ERROR_SCHEMA), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA), @ApiResponse(responseCode = "409", description = ERROR_SCHEMA)})
    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse open(@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Approved application reference.", content = @Content(schema = @Schema(implementation = AccountCreateRequest.class))) @Valid @RequestBody AccountCreateRequest r) {
        return service.open(r);
    }

    @Operation(tags = "Customer / Admin - Accounts", summary = "Get a credit-card account", description = "Intended customer/admin read API; authentication is not currently enforced.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Account found", content = @Content(schema = @Schema(implementation = AccountResponse.class))), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA)})
    @GetMapping("/accounts/{accountId}")
    AccountResponse account(@Parameter(description = "Credit-card account ID.", required = true, example = "5001") @PathVariable("accountId") Long accountId) {
        return service.account(accountId);
    }

    @Operation(tags = "Customer / Admin - Accounts", summary = "List accounts by CIF", description = "Intended customer/admin read API; authentication is not currently enforced.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Accounts for the CIF", content = @Content(schema = @Schema(implementation = AccountResponse.class))))
    @GetMapping("/accounts/cif/{cifId}")
    List<AccountResponse> accounts(@Parameter(description = "Customer CIF ID.", required = true, example = "101") @PathVariable Long cifId) {
        return service.accounts(cifId);
    }

    @Operation(tags = "Customer / Admin - Accounts", summary = "Get available credit limit", description = "Read-only available-limit view. It does not reserve credit or participate in concurrency control; HOLD is the authoritative reservation operation.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Available limit", content = @Content(schema = @Schema(implementation = LimitResponse.class))), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA)})
    @GetMapping("/accounts/{accountId}/available-limit")
    LimitResponse limit(@Parameter(description = "Credit-card account ID.", required = true, example = "5001") @PathVariable("accountId") Long accountId) {
        return service.limit(accountId);
    }

    @Operation(tags = "Internal - Payment Service", summary = "Create a credit hold", description = "Internal Payment Service API; authentication is not currently enforced. Atomically checks and reserves available credit. REFERENCE_ID provides idempotency, so a retry returns the original hold without reserving twice. HOLD is the authoritative credit-reservation operation.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Hold created or existing idempotent hold returned", content = @Content(schema = @Schema(implementation = HoldResponse.class))), @ApiResponse(responseCode = "400", description = ERROR_SCHEMA), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA), @ApiResponse(responseCode = "409", description = "Inactive account, insufficient credit, or reference belongs to another account")})
    @PostMapping("/accounts/{accountId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    HoldResponse createHold(@Parameter(description = "Credit-card account ID.", required = true, example = "5001") @PathVariable("accountId") Long accountId,
                            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Idempotent hold request.", content = @Content(schema = @Schema(implementation = HoldRequest.class))) @Valid @RequestBody HoldRequest request) {
        return service.createHold(accountId, request);
    }

    @Operation(tags = "Internal - Payment Service", summary = "Capture a credit hold", description = "Internal Payment Service API; authentication is not currently enforced. Changes HELD to CAPTURED and increases OUTSTANDING_AMOUNT. AVAILABLE_LIMIT is not reduced again. Repeated capture is idempotent.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Captured hold", content = @Content(schema = @Schema(implementation = HoldResponse.class))), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA), @ApiResponse(responseCode = "409", description = "Released hold cannot be captured")})
    @PostMapping("/accounts/{accountId}/holds/{holdId}/capture")
    HoldResponse captureHold(@Parameter(description = "Credit-card account ID.", required = true, example = "5001") @PathVariable("accountId") Long accountId,
                             @Parameter(description = "Hold ID.", required = true, example = "9001") @PathVariable("holdId") Long holdId) {
        return service.captureHold(accountId, holdId);
    }

    @Operation(tags = "Internal - Payment Service", summary = "Release a credit hold", description = "Internal Payment Service API; authentication is not currently enforced. Changes HELD to RELEASED and restores AVAILABLE_LIMIT. Repeated release is idempotent.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Released hold", content = @Content(schema = @Schema(implementation = HoldResponse.class))), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA), @ApiResponse(responseCode = "409", description = "Captured hold cannot be released")})
    @PostMapping("/accounts/{accountId}/holds/{holdId}/release")
    HoldResponse releaseHold(@Parameter(description = "Credit-card account ID.", required = true, example = "5001") @PathVariable("accountId") Long accountId,
                             @Parameter(description = "Hold ID.", required = true, example = "9001") @PathVariable("holdId") Long holdId) {
        return service.releaseHold(accountId, holdId);
    }

    @Operation(tags = "Internal - Payment Service", summary = "Record a bill payment", description = "Internal Payment Service API; authentication is not currently enforced. Only the portion required to clear current OUTSTANDING_AMOUNT is applied; any excess payment is ignored by Credit Card Service.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Account credit state updated", content = @Content(schema = @Schema(implementation = AccountResponse.class))), @ApiResponse(responseCode = "400", description = "Payment amount must be positive"), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA), @ApiResponse(responseCode = "409", description = ERROR_SCHEMA)})
    @PostMapping("/accounts/{accountId}/payments/billpaid")
    AccountResponse paid(@Parameter(description = "Credit-card account ID.", required = true, example = "5001") @PathVariable("accountId") Long accountId,
                         @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Positive bill-payment amount.", content = @Content(schema = @Schema(implementation = AmountRequest.class))) @Valid @RequestBody AmountRequest r) {
        return service.billPaid(accountId, r);
    }

    @Operation(tags = "Customer / Admin - Accounts", summary = "Begin account closure", description = "Intended customer/admin operation; authentication is not currently enforced. Sets CLOSURE_PENDING, requests Accounting clearance, and sets CLOSED only after Accounting confirms the final close.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Account is CLOSURE_PENDING or CLOSED", content = @Content(schema = @Schema(implementation = AccountResponse.class))), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA), @ApiResponse(responseCode = "409", description = "Account is already closed or cannot be closed in its current state")})
    @PostMapping("/accounts/{accountId}/close")
    AccountResponse close(@Parameter(description = "Credit-card account ID.", required = true, example = "5001") @PathVariable("accountId") Long accountId) {
        return service.close(accountId);
    }

    @Operation(tags = "Customer / Admin - Accounts", summary = "Get purchase interest rate", description = "Intended customer/admin read API; authentication is not currently enforced.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Interest-rate snapshot", content = @Content(schema = @Schema(implementation = InterestRateResponse.class))), @ApiResponse(responseCode = "404", description = ERROR_SCHEMA)})
    @GetMapping("/accounts/{accountId}/interest-rate")
    InterestRateResponse interest(@Parameter(description = "Credit-card account ID.", required = true, example = "5001") @PathVariable("accountId") Long accountId) {
        return service.interest(accountId);
    }

    @Operation(tags = "Internal - EOD Operations", summary = "Check EOD readiness", description = "Internal operational API; authentication is not currently enforced. Reports account-state and approved-application blockers for end-of-day processing.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "EOD readiness result", content = @Content(schema = @Schema(implementation = EodReadinessResponse.class))))
    @GetMapping("/accounts/eod/readiness")
    EodReadinessResponse eod() {
        return service.eod();
    }
}
