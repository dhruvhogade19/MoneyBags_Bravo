package com.moneybags.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.jwt.Jwt;

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
        var cif = clients.findByClientId("cif-service");

        assertThat(consumer).isNotNull();
        assertThat(consumer.getScopes()).contains("account:read", "payment:write");
        assertThat(consumer.getScopes()).doesNotContain("account:admin", "accounting:read", "kyc:review");
        assertThat(admin).isNotNull();
        assertThat(admin.getScopes()).contains("account:admin", "accounting:admin", "kyc:review");
        assertThat(payments).isNotNull();
        assertThat(payments.getScopes()).containsExactlyInAnyOrder(
                "deposit-payment:write", "card-payment:write", "accounting:service", "notification:service");
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
}
