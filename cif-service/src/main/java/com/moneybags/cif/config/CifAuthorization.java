package com.moneybags.cif.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("cifAuthorization")
public class CifAuthorization {
    private final boolean securityEnabled;

    public CifAuthorization(@Value("${moneybags.security.enabled:true}") boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    public boolean canAccess(Authentication authentication, Long cifId) {
        if (!securityEnabled) return true;
        if (authentication == null) return false;
        if (authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_BANK_ADMIN") || a.getAuthority().equals("SCOPE_cif:admin"))) return true;
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        String customerId = jwt.getToken().getClaimAsString("customer_id");
        return customerId != null && customerId.equals(String.valueOf(cifId));
    }

    public boolean canRegister(Authentication authentication) {
        if (!securityEnabled) return true;
        if (authentication == null) return false;
        if (authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_BANK_ADMIN") || a.getAuthority().equals("SCOPE_cif:service"))) {
            return true;
        }
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        boolean consumer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CONSUMER"));
        String userId = jwt.getToken().getClaimAsString("user_id");
        String customerId = jwt.getToken().getClaimAsString("customer_id");
        return consumer && userId != null && !userId.isBlank()
                && (customerId == null || customerId.isBlank());
    }
}
