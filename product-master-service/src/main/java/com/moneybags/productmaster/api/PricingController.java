package com.moneybags.productmaster.api;

import com.moneybags.productmaster.api.PricingDtos.RateQuoteResponse;
import com.moneybags.productmaster.api.ProductDtos.InterestRuleDto;
import com.moneybags.productmaster.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productCode}")
@Tag(name = "Product Pricing", description = "Interest policy and explainable rate quotes")
public class PricingController {
    private final PricingService service;

    public PricingController(PricingService service) {
        this.service = service;
    }

    @GetMapping("/rate-quote")
    @Operation(summary = "Calculate the exact rate effective on a date")
    public RateQuoteResponse quote(
            @PathVariable String productCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate quoteDate,
            @RequestParam(required = false) BigDecimal principal,
            @RequestParam(required = false) Integer tenureMonths) {
        return service.getQuote(productCode, quoteDate, principal, tenureMonths);
    }

    @PostMapping("/interest-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public InterestRuleDto addPolicy(@PathVariable String productCode,
                                     @Valid @RequestBody InterestRuleDto request) {
        return service.addPolicy(productCode, request);
    }

    @GetMapping("/interest-policies")
    public List<InterestRuleDto> policies(@PathVariable String productCode) {
        return service.policies(productCode);
    }
}
