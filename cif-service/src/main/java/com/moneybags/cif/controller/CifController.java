package com.moneybags.cif.controller;

import com.moneybags.cif.dto.request.CreateCifRequest;
import com.moneybags.cif.dto.request.UpdateCifRequest;
import com.moneybags.cif.dto.request.UpdateKycStatusRequest;
import com.moneybags.cif.dto.response.CifResponse;
import com.moneybags.cif.dto.response.CreditCardDetailsResponse;
import com.moneybags.cif.dto.response.CustomerContactDetailsResponse;
import com.moneybags.cif.dto.response.DepositCreationDetailsResponse;
import com.moneybags.cif.service.CifService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/cifs")
@Tag(
        name = "CIF Management",
        description = "APIs for customer CIF creation, updates, KYC status, and controlled data sharing"
)
public class CifController {

    private final CifService cifService;

    public CifController(CifService cifService) {
        this.cifService = cifService;
    }




    @PostMapping
    @Operation(
            summary = "Create a CIF",
            description = """
                Creates a customer CIF record with KYC status PENDING.
                After saving, CIF Service initiates KYC verification through KYC Service.
                """
    )
    public ResponseEntity<CifResponse> createCif(
            @Valid @RequestBody CreateCifRequest request
    ) {
        CifResponse response = cifService.createCif(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }




    @GetMapping("/{cifId}")
    @Operation(
            summary = "Get complete CIF details",
            description = "Returns complete CIF details for the specified CIF ID."
    )
    public ResponseEntity<CifResponse> getCifById(
            @PathVariable Long cifId
    ) {
        return ResponseEntity.ok(cifService.getCifById(cifId));
    }




    @PutMapping("/{cifId}")
    @Operation(
            summary = "Update CIF details",
            description = "Updates customer information for the specified CIF ID."
    )
    public ResponseEntity<CifResponse> updateCif(
            @PathVariable Long cifId,
            @Valid @RequestBody UpdateCifRequest request
    ) {
        return ResponseEntity.ok(cifService.updateCif(cifId, request));
    }




    @PatchMapping("/{cifId}/kyc-status")
    @Operation(
            summary = "Update KYC status",
            description = """
                Called by KYC Service to update the customer KYC status
                to APPROVED or REJECTED.
                """
    )
    public ResponseEntity<CifResponse> updateKycStatus(
            @PathVariable Long cifId,
            @Valid @RequestBody UpdateKycStatusRequest request
    ) {
        return ResponseEntity.ok(
                cifService.updateKycStatus(cifId, request)
        );
    }





    @GetMapping("/{cifId}/credit-card-details")
    @Operation(
            summary = "Get CIF details for Credit Card Service",
            description = """
                Returns only CIF ID, employment type, salary, and KYC status.
                This endpoint is intended for Credit Card Service.
                """
    )
    public ResponseEntity<CreditCardDetailsResponse> getCreditCardDetails(
            @PathVariable Long cifId
    ) {
        return ResponseEntity.ok(
                cifService.getCreditCardDetails(cifId)
        );
    }





    @GetMapping("/{cifId}/deposit-creation-details")
    @Operation(
            summary = "Get CIF details for Deposit Creation Service",
            description = """
                Returns only the customer data required for deposit account creation.
                This endpoint is intended for Deposit Creation Service.
                """
    )
    public ResponseEntity<DepositCreationDetailsResponse>
    getDepositCreationDetails(@PathVariable Long cifId) {

        return ResponseEntity.ok(
                cifService.getDepositCreationDetails(cifId)
        );
    }




    @GetMapping("/{cifId}/customer-contact-details")
    @Operation(
            summary = "Get contact details for Notification and Statement Services",
            description = """
                Returns only customer name, email, mobile number, and address.
                This endpoint is intended for Notification and Statement Services.
                """
    )
    public ResponseEntity<CustomerContactDetailsResponse>
    getCustomerContactDetails(@PathVariable Long cifId) {

        return ResponseEntity.ok(
                cifService.getCustomerContactDetails(cifId)
        );
    }
}