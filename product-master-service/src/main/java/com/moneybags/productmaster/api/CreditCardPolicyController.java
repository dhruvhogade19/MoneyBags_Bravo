package com.moneybags.productmaster.api;

import com.moneybags.productmaster.api.ProductDtos.CreditCardRuleDto;
import com.moneybags.productmaster.service.CreditCardPolicyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.moneybags.productmaster.service.ProductService;

@RestController
@RequestMapping("/api/products/{productCode}")
public class CreditCardPolicyController {
    private final CreditCardPolicyService service;
    private final ProductService products;
    private final CatalogueVisibility visibility;

    public CreditCardPolicyController(CreditCardPolicyService service, ProductService products,
                                      CatalogueVisibility visibility) {
        this.service = service;
        this.products = products;
        this.visibility = visibility;
    }

    @PostMapping("/credit-card-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditCardRuleDto addPolicy(@PathVariable String productCode,
                                       @Valid @RequestBody CreditCardRuleDto request) {
        return service.addPolicy(productCode, request);
    }

    @GetMapping("/credit-card-policies")
    public List<CreditCardRuleDto> policies(@PathVariable String productCode,
                                            Authentication authentication) {
        visibility.requireVisible(products.get(productCode), authentication);
        return service.policies(productCode);
    }
}
