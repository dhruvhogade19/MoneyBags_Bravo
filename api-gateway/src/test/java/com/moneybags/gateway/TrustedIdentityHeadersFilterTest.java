package com.moneybags.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class TrustedIdentityHeadersFilterTest {
    @Test
    void permitsOnlyReadOnlyPublicProductPathsWithoutIdentityHeaders() {
        assertThat(TrustedIdentityHeadersFilter.isPublicProductGet(
                HttpMethod.GET, "/api/products/active")).isTrue();
        assertThat(TrustedIdentityHeadersFilter.isPublicProductGet(
                HttpMethod.GET, "/api/v1/products/DEPOSIT-001")).isTrue();
        assertThat(TrustedIdentityHeadersFilter.isPublicProductGet(
                HttpMethod.POST, "/api/products")).isFalse();
        assertThat(TrustedIdentityHeadersFilter.isPublicProductGet(
                HttpMethod.GET, "/internal/v1/products/DEPOSIT-001")).isFalse();
        assertThat(TrustedIdentityHeadersFilter.isPublicProductGet(
                HttpMethod.GET, "/api/products-internal")).isFalse();
    }
}
