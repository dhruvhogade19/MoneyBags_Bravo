package com.moneybags.productmaster.api;

import com.moneybags.productmaster.api.ProductDtos.ProductResponse;
import com.moneybags.productmaster.domain.Enums.Status;
import com.moneybags.productmaster.exception.ProductExceptions.ProductNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
class CatalogueVisibility {
    private static final String BANK_ADMIN = "ROLE_BANK_ADMIN";

    Status listStatus(Status requestedStatus, Authentication authentication) {
        return canReadUnpublished(authentication) ? requestedStatus : Status.ACTIVE;
    }

    ProductResponse requireVisible(ProductResponse product, Authentication authentication) {
        if (product.status() == Status.ACTIVE || canReadUnpublished(authentication)) return product;
        // Use the same response as an unknown code so callers cannot enumerate unpublished products.
        throw new ProductNotFoundException(product.productCode());
    }

    private boolean canReadUnpublished(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> BANK_ADMIN.equals(authority.getAuthority()));
    }
}
