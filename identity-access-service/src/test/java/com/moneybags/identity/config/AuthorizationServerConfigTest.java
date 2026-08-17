package com.moneybags.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
        var billing = clients.findByClientId("bill-generation-service");
        var cif = clients.findByClientId("cif-service");
        var eod = clients.findByClientId("eod-reconciliation-service");

        assertThat(consumer).isNotNull();
        assertThat(consumer.getScopes()).contains("account:read", "payment:write", "billing:read");
        assertThat(consumer.getPostLogoutRedirectUris()).containsExactly("http://127.0.0.1:8000/");
        assertThat(consumer.getScopes()).doesNotContain("account:admin", "accounting:read", "kyc:review");
        assertThat(admin).isNotNull();
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
        assertThat(eod).isNotNull();
        assertThat(eod.getScopes()).containsExactlyInAnyOrder(
                "payment:service", "account:service", "card:eod", "billing:service",
                "accounting:service", "notification:service", "statements:service");
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
    void persistsTheSigningKeyAcrossIdentityRestarts() throws Exception {
        var path = java.nio.file.Path.of("target", "test-signing-key-" + UUID.randomUUID() + ".json");
        try {
            var first = AuthorizationServerConfig.loadOrCreateSigningKey(path);
            var second = AuthorizationServerConfig.loadOrCreateSigningKey(path);

            assertThat(second.getKeyID()).isEqualTo(first.getKeyID());
            assertThat(second.toRSAPublicKey()).isEqualTo(first.toRSAPublicKey());
            assertThat(second.isPrivate()).isTrue();
        } finally {
            java.nio.file.Files.deleteIfExists(path);
        }
    }
}
