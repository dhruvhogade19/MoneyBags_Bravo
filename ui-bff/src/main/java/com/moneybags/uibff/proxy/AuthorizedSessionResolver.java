package com.moneybags.uibff.proxy;

import com.moneybags.uibff.api.BffApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class AuthorizedSessionResolver {
    private final OAuth2AuthorizedClientManager clients;

    public AuthorizedSessionResolver(OAuth2AuthorizedClientManager clients) {
        this.clients = clients;
    }

    public AuthorizedSession resolve(Authentication authentication,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)
                || !(oauth.getPrincipal() instanceof OidcUser user)) {
            throw new BffApiException(HttpStatus.UNAUTHORIZED, "An authenticated Moneybags session is required");
        }
        var authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(oauth.getAuthorizedClientRegistrationId())
                .principal(oauth)
                .attribute(HttpServletRequest.class.getName(), request)
                .attribute(HttpServletResponse.class.getName(), response)
                .build();
        var client = clients.authorize(authorizeRequest);
        if (client == null || client.getAccessToken() == null) {
            throw new BffApiException(HttpStatus.UNAUTHORIZED, "The Moneybags session has expired");
        }
        String tenantId = user.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            throw new BffApiException(HttpStatus.FORBIDDEN, "The identity token does not contain a tenant");
        }
        List<String> roles = user.getClaimAsStringList("roles");
        if (roles == null) roles = List.of();
        roles = List.copyOf(roles);

        String actorId = null;
        if (roles.contains("BANK_ADMIN")) {
            actorId = firstNonBlank(
                    user.getClaimAsString("user_id"),
                    user.getClaimAsString("preferred_username"));
            if (actorId == null) {
                throw new BffApiException(HttpStatus.FORBIDDEN,
                        "The administrator identity token does not contain an audit actor");
            }
        }
        return new AuthorizedSession(
                client.getAccessToken().getTokenValue(),
                tenantId,
                roles,
                user.getClaimAsString("customer_id"),
                actorId);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    public record AuthorizedSession(
            String accessToken,
            String tenantId,
            List<String> roles,
            String customerId,
            String actorId) {

        public boolean isBankAdmin() {
            return roles.contains("BANK_ADMIN");
        }

        public boolean isCustomer() {
            return roles.contains("CONSUMER");
        }
    }
}
