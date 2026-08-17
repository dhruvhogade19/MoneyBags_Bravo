package com.moneybags.uibff.proxy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moneybags.uibff.api.BffApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

class CustomerKycAccessGuardTest {
    private final CifKycApprovalClient approvals = mock(CifKycApprovalClient.class);
    private final CustomerKycAccessGuard guard = new CustomerKycAccessGuard(approvals);

    @Test
    void administratorsAreNotSubjectToTheCustomerKycGate() {
        var admin = session(List.of("BANK_ADMIN"), null, "admin-user-7");

        guard.authorize(admin, HttpMethod.POST, "/api/v1/journals", "correlation-1");

        verify(approvals, never()).isApproved(admin, "correlation-1");
    }

    @Test
    void onboardingRequestsRemainAvailableWithoutALinkedCif() {
        var customer = session(List.of("CONSUMER"), null, null);

        guard.authorize(customer, HttpMethod.POST, "/api/v1/cifs", "correlation-1");
        guard.authorize(customer, HttpMethod.GET, "/api/products/active", "correlation-1");

        verify(approvals, never()).isApproved(customer, "correlation-1");
    }

    @Test
    void approvedCustomersCanReachBankingRoutes() {
        var customer = session(List.of("CONSUMER"), "101", null);
        when(approvals.isApproved(customer, "correlation-1")).thenReturn(true);

        guard.authorize(customer, HttpMethod.POST, "/api/deposit-accounts", "correlation-1");

        verify(approvals).isApproved(customer, "correlation-1");
    }

    @Test
    void pendingCustomersAreDeniedBankingRoutes() {
        var customer = session(List.of("CONSUMER"), "101", null);
        when(approvals.isApproved(customer, "correlation-1")).thenReturn(false);

        assertThatThrownBy(() -> guard.authorize(
                customer, HttpMethod.POST, "/api/v1/payments", "correlation-1"))
                .isInstanceOfSatisfying(BffApiException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.status())
                                .isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("KYC approval is required");
    }

    @Test
    void customersWithoutAValidCifClaimAreDeniedWithoutAnUpstreamLookup() {
        var customer = session(List.of("CONSUMER"), "not-a-cif", null);

        assertThatThrownBy(() -> guard.authorize(
                customer, HttpMethod.GET, "/api/deposit-accounts", "correlation-1"))
                .isInstanceOf(BffApiException.class)
                .hasMessageContaining("Complete the customer profile");
        verify(approvals, never()).isApproved(customer, "correlation-1");
    }

    private static AuthorizedSessionResolver.AuthorizedSession session(
            List<String> roles, String customerId, String actorId) {
        return new AuthorizedSessionResolver.AuthorizedSession(
                "access-token", "moneybags", roles, customerId, actorId);
    }
}
