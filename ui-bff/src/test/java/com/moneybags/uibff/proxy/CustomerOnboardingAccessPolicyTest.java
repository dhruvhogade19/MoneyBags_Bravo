package com.moneybags.uibff.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CustomerOnboardingAccessPolicyTest {
    @Test
    void permitsOnlyTheNarrowPreApprovalSurface() {
        assertAllowed(HttpMethod.POST, "/api/v1/cifs");
        assertAllowed(HttpMethod.GET, "/api/v1/cifs/101");
        assertAllowed(HttpMethod.PUT, "/api/v1/cifs/101");
        assertAllowed(HttpMethod.GET, "/api/v1/kycs?not-part-of-path");
        assertAllowed(HttpMethod.GET, "/api/v1/kycs/9");
        assertAllowed(HttpMethod.GET, "/api/v1/kycs/9/documents");
        assertAllowed(HttpMethod.GET, "/api/v1/kycs/9/documents/12");
        assertAllowed(HttpMethod.POST, "/api/v1/kycs/9/documents");
        assertAllowed(HttpMethod.GET, "/api/notifications");
        assertAllowed(HttpMethod.GET, "/api/notifications/7");
        assertAllowed(HttpMethod.GET, "/api/products/active");
        assertAllowed(HttpMethod.GET, "/api/v1/products/CARD-1");
        assertAllowed(HttpMethod.GET, "/api/benchmarks/RBI/history");
    }

    @Test
    void rejectsBankingAdminAndInternalStyleOperationsBeforeApproval() {
        assertBlocked(HttpMethod.POST, "/api/deposit-accounts");
        assertBlocked(HttpMethod.POST, "/api/v1/payments");
        assertBlocked(HttpMethod.GET, "/api/v1/bills");
        assertBlocked(HttpMethod.POST, "/api/products");
        assertBlocked(HttpMethod.PATCH, "/api/v1/kycs/9/decision");
        assertBlocked(HttpMethod.POST, "/api/v1/kycs");
        assertBlocked(HttpMethod.GET, "/api/v1/kycs/admin/work-queue");
        assertBlocked(HttpMethod.GET, "/api/v1/cifs/101/deposit-creation-details");
        assertBlocked(HttpMethod.GET, "/api/products-admin");
    }

    private static void assertAllowed(HttpMethod method, String path) {
        String requestPath = path.split("\\?", 2)[0];
        assertThat(CustomerOnboardingAccessPolicy.isAllowed(method, requestPath))
                .as("%s %s", method, requestPath)
                .isTrue();
    }

    private static void assertBlocked(HttpMethod method, String path) {
        assertThat(CustomerOnboardingAccessPolicy.isAllowed(method, path))
                .as("%s %s", method, path)
                .isFalse();
    }
}
