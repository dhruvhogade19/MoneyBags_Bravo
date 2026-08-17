package com.moneybags.uibff.proxy;

import com.moneybags.uibff.api.BffApiException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CustomerKycAccessGuard {
    private final CifKycApprovalClient approvals;

    CustomerKycAccessGuard(CifKycApprovalClient approvals) {
        this.approvals = approvals;
    }

    void authorize(AuthorizedSessionResolver.AuthorizedSession session,
                   HttpMethod method,
                   String path,
                   String correlationId) {
        if (session.isBankAdmin()) return;
        if (!session.isCustomer()) {
            throw new BffApiException(HttpStatus.FORBIDDEN,
                    "The signed-in identity does not have a Moneybags application role");
        }
        if (CustomerOnboardingAccessPolicy.isAllowed(method, path)) return;
        if (session.customerId() == null || !session.customerId().matches("[0-9]+")) {
            throw new BffApiException(HttpStatus.FORBIDDEN,
                    "Complete the customer profile and KYC approval before using banking services");
        }
        if (!approvals.isApproved(session, correlationId)) {
            throw new BffApiException(HttpStatus.FORBIDDEN,
                    "KYC approval is required before using banking services");
        }
    }
}
