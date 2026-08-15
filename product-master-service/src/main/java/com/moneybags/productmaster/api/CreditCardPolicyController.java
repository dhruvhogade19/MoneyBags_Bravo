package com.moneybags.productmaster.api;

import com.moneybags.productmaster.api.ProductDtos.CreditCardRuleDto;
import com.moneybags.productmaster.service.CreditCardPolicyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productCode}")
public class CreditCardPolicyController {
    private final CreditCardPolicyService service;

    public CreditCardPolicyController(CreditCardPolicyService service) {
        this.service = service;
    }

    @PostMapping("/credit-card-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditCardRuleDto addPolicy(@PathVariable String productCode,
                                       @Valid @RequestBody CreditCardRuleDto request) {
        return service.addPolicy(productCode, request);
    }

    @GetMapping("/credit-card-policies")
    public List<CreditCardRuleDto> policies(@PathVariable String productCode) {
        return service.policies(productCode);
    }
}
