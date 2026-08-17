package com.moneybags.payments.config;

import com.moneybags.payments.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("paymentAuthorization")
public class PaymentAuthorization {
    private final PaymentRepository payments;
    private final boolean securityEnabled;

    public PaymentAuthorization(PaymentRepository payments,
                                @Value("${moneybags.security.enabled:true}") boolean securityEnabled) {
        this.payments = payments;
        this.securityEnabled = securityEnabled;
    }

    public boolean canUseCustomer(Authentication authentication, Long customerId) {
        return !securityEnabled || privileged(authentication) || owns(authentication, customerId);
    }

    public boolean canAccessPayment(Authentication authentication, String paymentId) {
        if (!securityEnabled || privileged(authentication)) return true;
        return payments.findById(paymentId)
                .map(payment -> owns(authentication, payment.getRequestorCifId())).orElse(false);
    }

    private boolean privileged(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_BANK_ADMIN") || a.getAuthority().equals("SCOPE_payment:admin")
                        || a.getAuthority().equals("SCOPE_payment:service"));
    }

    private boolean owns(Authentication authentication, Long customerId) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        String claim = jwt.getToken().getClaimAsString("customer_id");
        return claim != null && claim.equals(String.valueOf(customerId));
    }
}
