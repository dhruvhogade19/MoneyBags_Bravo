package com.moneybags.uibff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneybags.uibff.api.BffApiException;
import org.junit.jupiter.api.Test;

class ProxyPathPolicyTest {
    @Test
    void mapsOnlyApiPaths() {
        assertThat(ProxyPathPolicy.authenticatedGatewayPath(
                "/api/proxy/api/v1/payments/42")).isEqualTo("/api/v1/payments/42");
        assertThat(ProxyPathPolicy.publicProductGatewayPath(
                "/api/public/products/SAV-REG-001")).isEqualTo("/api/products/SAV-REG-001");
        assertThat(ProxyPathPolicy.authenticatedGatewayPath(
                "/api/proxy/api/products/DRAFT-ONLY")).isEqualTo("/api/products/DRAFT-ONLY");
    }

    @Test
    void rejectsInternalTraversalAndEncodedInternalPaths() {
        assertBlocked("/api/proxy/internal/v1/jobs");
        assertBlocked("/api/proxy/api/internal/v1/jobs");
        assertBlocked("/api/proxy/api/products/../internal/jobs");
        assertBlocked("/api/proxy/api/%2569nternal/jobs");
        assertBlocked("/api/proxy/actuator/health");
        assertPublicBlocked("/api/public/products/DRAFT-ONLY/pricing");
        assertPublicBlocked("/api/public/products/../internal");
    }

    private static void assertBlocked(String path) {
        assertThatThrownBy(() -> ProxyPathPolicy.authenticatedGatewayPath(path))
                .isInstanceOf(BffApiException.class)
                .hasMessageContaining("internal and traversal paths are blocked");
    }

    private static void assertPublicBlocked(String path) {
        assertThatThrownBy(() -> ProxyPathPolicy.publicProductGatewayPath(path))
                .isInstanceOf(BffApiException.class)
                .hasMessageContaining("internal and traversal paths are blocked");
    }
}
