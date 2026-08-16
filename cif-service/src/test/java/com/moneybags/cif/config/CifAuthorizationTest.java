package com.moneybags.cif.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CifAuthorizationTest {
    private final CifAuthorization authorization = new CifAuthorization(true);

    @Test
    void consumerCanAccessOnlyTheCifInTheirSignedClaim() {
        var consumer = token("42", "SCOPE_cif:read");

        assertThat(authorization.canAccess(consumer, 42L)).isTrue();
        assertThat(authorization.canAccess(consumer, 43L)).isFalse();
    }

    @Test
    void bankAdministratorCanAccessAnyCif() {
        assertThat(authorization.canAccess(token(null, "ROLE_BANK_ADMIN"), 999L)).isTrue();
    }

    @Test
    void unlinkedConsumerCanRegisterExactlyUntilCustomerClaimExists() {
        assertThat(authorization.canRegister(token(null, "user-42", "ROLE_CONSUMER"))).isTrue();
        assertThat(authorization.canRegister(token("42", "user-42", "ROLE_CONSUMER"))).isFalse();
    }

    private static JwtAuthenticationToken token(String customerId, String authority) {
        return token(customerId, null, authority);
    }

    private static JwtAuthenticationToken token(String customerId, String userId, String authority) {
        Jwt.Builder jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("user");
        if (customerId != null) jwt.claim("customer_id", customerId);
        if (userId != null) jwt.claim("user_id", userId);
        return new JwtAuthenticationToken(jwt.build(), List.of(new SimpleGrantedAuthority(authority)));
    }
}
