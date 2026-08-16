package com.moneybags.kycservice.config;

import com.moneybags.kycservice.repository.KycRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("kycAuthorization")
public class KycAuthorization {
    private final KycRepository repository;
    private final boolean securityEnabled;

    public KycAuthorization(KycRepository repository,
                            @Value("${moneybags.security.enabled:true}") boolean securityEnabled) {
        this.repository = repository;
        this.securityEnabled = securityEnabled;
    }

    public boolean canAccess(Authentication authentication, Long kycId) {
        if (!securityEnabled) return true;
        if (service(authentication)) return true;
        return repository.findById(kycId).map(kyc ->
                (reviewer(authentication) && tenantMatches(authentication, kyc.getTenantId()))
                        || (owns(authentication, kyc.getCifId())
                        && tenantMatches(authentication, kyc.getTenantId()))).orElse(false);
    }

    public boolean canAccessCif(Authentication authentication, Long cifId) {
        if (!securityEnabled || service(authentication)) return true;
        var cases = repository.findAllByCifIdOrderByCreatedAtDesc(cifId);
        if (owns(authentication, cifId)) {
            return cases.isEmpty() || cases.stream()
                    .anyMatch(kyc -> tenantMatches(authentication, kyc.getTenantId()));
        }
        return reviewer(authentication) && cases.stream()
                .anyMatch(kyc -> tenantMatches(authentication, kyc.getTenantId()));
    }

    private boolean service(Authentication authentication) {
        return has(authentication, "SCOPE_kyc:service");
    }

    private boolean reviewer(Authentication authentication) {
        return has(authentication, "ROLE_BANK_ADMIN") || has(authentication, "SCOPE_kyc:review");
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals(authority));
    }

    private boolean owns(Authentication authentication, Long cifId) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        String customerId = jwt.getToken().getClaimAsString("customer_id");
        return customerId != null && customerId.equals(String.valueOf(cifId));
    }

    private boolean tenantMatches(Authentication authentication, String tenantId) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        String claim = jwt.getToken().getClaimAsString("tenant_id");
        return claim != null && claim.equals(tenantId);
    }
}
