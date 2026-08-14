package com.moneybags.productmaster.api;

import com.moneybags.productmaster.api.ProductDtos.AccountOpeningValidationRequest;
import com.moneybags.productmaster.api.ProductDtos.CreditCardApplicationValidationRequest;
import com.moneybags.productmaster.api.ProductDtos.CreditCardValidationResponse;
import com.moneybags.productmaster.api.ProductDtos.ValidationResponse;
import com.moneybags.productmaster.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/products")
@Tag(name = "Internal Product Decisions", description = "Trusted product decisions for Moneybags services")
public class InternalProductController {
    private final ProductService service;

    public InternalProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping("/{productCode}/validate-account-opening")
    @Operation(summary = "Validate and resolve the product terms for a deposit account opening")
    public ValidationResponse validateAccountOpening(
            @PathVariable String productCode,
            @Valid @RequestBody AccountOpeningValidationRequest request) {
        return service.validateAccountOpening(productCode, request);
    }

    @PostMapping("/{productCode}/validate-credit-card-application")
    @Operation(summary = "Validate a credit-card application")
    public CreditCardValidationResponse validateCreditCardApplication(
            @PathVariable String productCode,
            @Valid @RequestBody CreditCardApplicationValidationRequest request) {
        return service.validateCreditCardApplication(productCode, request);
    }
}
