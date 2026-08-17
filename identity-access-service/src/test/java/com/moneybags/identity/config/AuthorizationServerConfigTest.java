package com.moneybags.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.moneybags.identity.user.BankPrincipal;
import com.moneybags.identity.user.BankUser;
import com.moneybags.identity.user.BankUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationServerConfigTest {

    private final AuthorizationServerConfig config = new AuthorizationServerConfig();

    @Test
    void separatesConsumerAdministratorAndServiceClientScopes() {
        var clients = config.registeredClientRepository(
                PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                "http://127.0.0.1:8000/callback",
                "http://127.0.0.1:8001/callback",
                "test-service-secret");

        var consumer = clients.findByClientId("moneybags-consumer");
        var admin = clients.findByClientId("moneybags-admin");
        var payments = clients.findByClientId("payments-service");
        var billing = clients.findByClientId("bill-generation-service");
        var cif = clients.findByClientId("cif-service");

        assertThat(consumer).isNotNull();
        assertThat(consumer.getPostLogoutRedirectUris()).containsExactly("http://127.0.0.1:8000/callback");
        assertThat(consumer.getScopes()).contains("account:read", "payment:write", "billing:read");
        assertThat(consumer.getScopes()).doesNotContain("account:admin", "accounting:read", "kyc:review");
        assertThat(admin).isNotNull();
        assertThat(admin.getPostLogoutRedirectUris()).containsExactly("http://127.0.0.1:8001/callback");
        assertThat(admin.getScopes()).contains("account:admin", "accounting:admin", "kyc:review");
        assertThat(payments).isNotNull();
        assertThat(payments.getScopes()).containsExactlyInAnyOrder(
                "deposit-payment:write", "card-payment:write", "accounting:service", "notification:service",
                "billing:service");
        assertThat(billing).isNotNull();
        assertThat(billing.getScopes()).containsExactlyInAnyOrder(
                "product:validate", "card:billing", "accounting:service", "notification:service");
        assertThat(cif).isNotNull();
        assertThat(cif.getScopes()).containsExactlyInAnyOrder("kyc:service", "identity:service");
    }

    @Test
    void convertsJwtRoleClaimsForMethodAuthorization() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("scope", "identity:admin")
                .claim("roles", List.of("BANK_ADMIN"))
                .build();

        var authentication = AuthorizationServerConfig.resourceServerJwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities()).extracting("authority")
                .contains("SCOPE_identity:admin", "ROLE_BANK_ADMIN");
    }

    @Test
    void refreshedClaimsReloadTheCurrentCustomerLink() {
        BankUser current = new BankUser("customer@test.com", "encoded", "42", "moneybags", "CONSUMER");
        BankPrincipal stale = new BankPrincipal(current.getId(), current.getUsername(), "encoded", null,
                "moneybags", true, true, List.of(new SimpleGrantedAuthority("ROLE_CONSUMER")));
        BankUserRepository users = mock(BankUserRepository.class);
        when(users.findById(current.getId())).thenReturn(Optional.of(current));

        var resolved = AuthorizationServerConfig.resolveBankIdentity(stale, users);

        assertThat(resolved.customerId()).isEqualTo("42");
        assertThat(resolved.tenantId()).isEqualTo("moneybags");
        assertThat(resolved.roles()).containsExactly("CONSUMER");
    }
}
