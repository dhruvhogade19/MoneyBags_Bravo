package com.moneybags.uibff.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class SessionControllerTest {
    private final SessionController controller = new SessionController();
    private final DefaultCsrfToken csrf = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-value");

    @Test
    void reportsAnAnonymousSessionAndLoginLinks() {
        var session = controller.session(null, csrf);

        assertThat(session.authenticated()).isFalse();
        assertThat(session.loginLinks()).containsEntry(
                "customer", "/oauth2/authorization/moneybags-consumer");
        assertThat(session.csrf().token()).isEqualTo("csrf-value");
    }

    @Test
    void reportsConsumerProfileOnboardingUntilCifLinksTheIdentity() {
        var authentication = authentication("moneybags-consumer", List.of("CONSUMER"), null);

        var session = controller.session(authentication, csrf);

        assertThat(session.authenticated()).isTrue();
        assertThat(session.username()).isEqualTo("customer@moneybags.local");
        assertThat(session.roles()).containsExactly("CONSUMER");
        assertThat(session.tenantId()).isEqualTo("moneybags");
        assertThat(session.customerId()).isNull();
        assertThat(session.onboardingStatus()).isEqualTo("PENDING_PROFILE");
    }

    @Test
    void reportsLinkedConsumersAsPendingKycAndAdminsAsApproved() {
        assertThat(controller.session(authentication(
                "moneybags-consumer", List.of("CONSUMER"), "101"), csrf).onboardingStatus())
                .isEqualTo("PENDING_KYC");
        assertThat(controller.session(authentication(
                "moneybags-admin", List.of("BANK_ADMIN"), null), csrf).onboardingStatus())
                .isEqualTo("APPROVED");
    }

    private static OAuth2AuthenticationToken authentication(
            String registrationId, List<String> roles, String customerId) {
        var claims = new java.util.LinkedHashMap<String, Object>(Map.of(
                "sub", "user-1",
                "preferred_username", "customer@moneybags.local",
                "tenant_id", "moneybags",
                "roles", roles));
        if (customerId != null) claims.put("customer_id", customerId);
        OidcIdToken token = new OidcIdToken("id-token", Instant.now(),
                Instant.now().plusSeconds(60), claims);
        DefaultOidcUser user = new DefaultOidcUser(List.of(), token, "preferred_username");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), registrationId);
    }
}
