package com.moneybags.creditcard.config;

import com.moneybags.creditcard.repository.CreditCardAccountRepository;
import com.moneybags.creditcard.repository.CreditCardApplicationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("creditCardAuthorization")
public class CreditCardAuthorization {
    private final CreditCardApplicationRepository applications;
    private final CreditCardAccountRepository accounts;
    private final boolean securityEnabled;

    public CreditCardAuthorization(CreditCardApplicationRepository applications,
                                   CreditCardAccountRepository accounts,
                                   @Value("${moneybags.security.enabled:true}") boolean securityEnabled) {
        this.applications = applications;
        this.accounts = accounts;
        this.securityEnabled = securityEnabled;
    }

    public boolean canAccessCif(Authentication authentication, Long cifId) {
        return !securityEnabled || privileged(authentication) || owns(authentication, cifId);
    }

    public boolean canAccessApplication(Authentication authentication, Long applicationId) {
        if (!securityEnabled || privileged(authentication)) return true;
        return applications.findById(applicationId).map(value -> owns(authentication, value.cifId)).orElse(false);
    }

    public boolean canAccessAccount(Authentication authentication, Long accountId) {
        if (!securityEnabled || privileged(authentication)) return true;
        return accounts.findById(accountId).map(value -> owns(authentication, value.cifId)).orElse(false);
    }

    private boolean privileged(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_BANK_ADMIN") || a.getAuthority().equals("SCOPE_card:admin")
                        || a.getAuthority().equals("SCOPE_card-payment:write"));
    }

    private boolean owns(Authentication authentication, Long cifId) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        String customerId = jwt.getToken().getClaimAsString("customer_id");
        return customerId != null && customerId.equals(String.valueOf(cifId));
    }
}
