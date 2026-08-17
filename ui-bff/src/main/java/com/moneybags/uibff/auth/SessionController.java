package com.moneybags.uibff.auth;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    private static final Map<String, String> LOGIN_LINKS = Map.of(
            "customer", "/oauth2/authorization/moneybags-consumer",
            "admin", "/oauth2/authorization/moneybags-admin");

    @GetMapping({"/api/session", "/api/session/me"})
    public SessionView session(Authentication authentication, CsrfToken csrfToken) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)
                || !(oauth.getPrincipal() instanceof OidcUser user)) {
            return new SessionView(false, null, List.of(), null, null, null, null,
                    LOGIN_LINKS, CsrfView.from(csrfToken));
        }

        List<String> roles = user.getClaimAsStringList("roles");
        if (roles == null) roles = List.of();
        String customerId = user.getClaimAsString("customer_id");
        return new SessionView(
                true,
                username(user),
                List.copyOf(roles),
                user.getClaimAsString("tenant_id"),
                customerId,
                onboardingStatus(roles, customerId),
                oauth.getAuthorizedClientRegistrationId(),
                LOGIN_LINKS,
                CsrfView.from(csrfToken));
    }

    @GetMapping("/api/session/login-links")
    public Map<String, String> loginLinks() {
        return LOGIN_LINKS;
    }

    private static String username(OidcUser user) {
        String username = user.getClaimAsString("preferred_username");
        return username == null ? user.getName() : username;
    }

    private static String onboardingStatus(List<String> roles, String customerId) {
        if (roles.contains("BANK_ADMIN")) return "APPROVED";
        if (!roles.contains("CONSUMER")) return null;
        return customerId == null || customerId.isBlank() ? "PENDING_PROFILE" : "PENDING_KYC";
    }

    public record SessionView(
            boolean authenticated,
            String username,
            List<String> roles,
            String tenantId,
            String customerId,
            String onboardingStatus,
            String clientRegistrationId,
            Map<String, String> loginLinks,
            CsrfView csrf) {}

    public record CsrfView(String headerName, String parameterName, String token) {
        static CsrfView from(CsrfToken token) {
            return new CsrfView(token.getHeaderName(), token.getParameterName(), token.getToken());
        }
    }
}
