package com.moneybags.kycservice.config;

import com.moneybags.kycservice.entity.Kyc;
import com.moneybags.kycservice.repository.KycRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KycAuthorizationTest {

    private final KycRepository repository = mock(KycRepository.class);
    private final KycAuthorization authorization = new KycAuthorization(repository, true);

    @Test
    void consumerMustOwnTheCifAndMatchItsTenant() {
        Kyc kyc = kyc(42L, "tenant-a");
        when(repository.findById(7L)).thenReturn(Optional.of(kyc));
        when(repository.findAllByCifIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(kyc));

        assertThat(authorization.canAccess(token("42", "tenant-a", "ROLE_CONSUMER"), 7L)).isTrue();
        assertThat(authorization.canAccess(token("42", "tenant-b", "ROLE_CONSUMER"), 7L)).isFalse();
        assertThat(authorization.canAccess(token("43", "tenant-a", "ROLE_CONSUMER"), 7L)).isFalse();
        assertThat(authorization.canAccessCif(token("42", "tenant-a", "ROLE_CONSUMER"), 42L)).isTrue();
        assertThat(authorization.canAccessCif(token("42", "tenant-b", "ROLE_CONSUMER"), 42L)).isFalse();
    }

    @Test
    void administratorIsRestrictedToTheirSignedTenant() {
        when(repository.findById(7L)).thenReturn(Optional.of(kyc(42L, "tenant-a")));

        assertThat(authorization.canAccess(token(null, "tenant-a", "ROLE_BANK_ADMIN"), 7L)).isTrue();
        assertThat(authorization.canAccess(token(null, "tenant-b", "ROLE_BANK_ADMIN"), 7L)).isFalse();
    }

    @Test
    void KycServicePrincipalCanCreateAndReadTrustedSnapshots() {
        assertThat(authorization.canAccess(token(null, null, "SCOPE_kyc:service"), 999L)).isTrue();
        assertThat(authorization.canAccessCif(token(null, null, "SCOPE_kyc:service"), 42L)).isTrue();
    }

    private static Kyc kyc(Long cifId, String tenantId) {
        Kyc kyc = new Kyc();
        kyc.setCifId(cifId);
        kyc.setTenantId(tenantId);
        return kyc;
    }

    private static JwtAuthenticationToken token(String customerId, String tenantId, String authority) {
        Jwt.Builder jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("principal");
        if (customerId != null) jwt.claim("customer_id", customerId);
        if (tenantId != null) jwt.claim("tenant_id", tenantId);
        return new JwtAuthenticationToken(jwt.build(), List.of(new SimpleGrantedAuthority(authority)));
    }
}
