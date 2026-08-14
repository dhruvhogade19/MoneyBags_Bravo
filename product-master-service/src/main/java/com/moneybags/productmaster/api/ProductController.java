package com.moneybags.productmaster.api;

import com.moneybags.productmaster.api.ProductDtos.*;
import com.moneybags.productmaster.domain.Enums.*;
import com.moneybags.productmaster.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/products", "/api/v1/products"})
@Tag(name = "Product Master", description = "Banking product definitions and rule catalogue")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft product")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return service.create(request);
    }

    @GetMapping
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Subtype subtype,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate activeOn,
            @PageableDefault(size = 20, sort = "productCode") Pageable pageable) {
        return service.findAll(category, subtype, status, productName, activeOn, pageable);
    }

    @GetMapping("/{productCode}")
    public ProductResponse get(@PathVariable String productCode) {
        return service.get(productCode);
    }

    @PutMapping("/{productCode}")
    public ProductResponse update(@PathVariable String productCode,
                                  @Valid @RequestBody ProductRequest request) {
        return service.update(productCode, request);
    }

    @PatchMapping("/{productCode}/status")
    public ProductResponse status(@PathVariable String productCode,
                                  @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(productCode, request);
    }

    @DeleteMapping("/{productCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discontinue(@PathVariable String productCode,
                            @RequestParam(defaultValue = "system") String changedBy) {
        service.discontinue(productCode, changedBy);
    }

    @GetMapping("/active")
    public List<ProductResponse> active() {
        return service.active(null);
    }

    @GetMapping("/category/{category}/active")
    public List<ProductResponse> activeCategory(@PathVariable Category category) {
        return service.active(category);
    }

    @GetMapping("/category/CREDIT_CARD/active/minimal")
    @Operation(summary = "Get the compact active credit-card catalogue")
    public List<MinimalCreditCardProductResponse> activeCreditCardsMinimal() {
        return service.minimalCreditCards();
    }

    @GetMapping("/{productCode}/minimal")
    @Operation(summary = "Get one compact credit-card product")
    public MinimalCreditCardProductResponse creditCardMinimal(@PathVariable String productCode) {
        return service.minimalCreditCard(productCode);
    }

    @GetMapping("/{productCode}/eligibility")
    public List<EligibilityRuleDto> eligibility(@PathVariable String productCode) {
        return service.eligibility(productCode);
    }

    @GetMapping("/{productCode}/pricing")
    public ProductResponse pricing(@PathVariable String productCode) {
        return service.pricing(productCode);
    }

    @PostMapping("/{productCode}/validate-account-opening")
    public ValidationResponse accountOpening(
            @PathVariable String productCode,
            @Valid @RequestBody AccountOpeningValidationRequest request) {
        return service.validateAccountOpening(productCode, request);
    }

    @PostMapping("/{productCode}/validate-credit-card-application")
    public CreditCardValidationResponse creditCardApplication(
            @PathVariable String productCode,
            @Valid @RequestBody CreditCardApplicationValidationRequest request) {
        return service.validateCreditCardApplication(productCode, request);
    }
}
