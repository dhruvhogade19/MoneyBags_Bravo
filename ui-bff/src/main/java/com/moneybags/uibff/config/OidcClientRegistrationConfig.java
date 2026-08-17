package com.moneybags.uibff.config;

import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class OidcClientRegistrationConfig {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties properties) {
        var registrations = properties.getRegistration().entrySet().stream()
                .map(entry -> registration(entry.getKey(), entry.getValue(), properties.getProvider()))
                .toList();
        if (registrations.isEmpty()) {
            throw new IllegalStateException("At least one UI OAuth client registration is required");
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    private static ClientRegistration registration(
            String registrationId,
            OAuth2ClientProperties.Registration registration,
            Map<String, OAuth2ClientProperties.Provider> providers) {
        String providerId = StringUtils.hasText(registration.getProvider())
                ? registration.getProvider() : registrationId;
        OAuth2ClientProperties.Provider provider = providers.get(providerId);
        if (provider == null || !StringUtils.hasText(provider.getIssuerUri())) {
            throw new IllegalStateException("OAuth provider '" + providerId + "' must define issuer-uri");
        }

        String issuer = trimTrailingSlash(provider.getIssuerUri());
        String authorizationUri = valueOrDefault(provider.getAuthorizationUri(), issuer + "/oauth2/authorize");
        String tokenUri = valueOrDefault(provider.getTokenUri(), issuer + "/oauth2/token");
        String jwkSetUri = valueOrDefault(provider.getJwkSetUri(), issuer + "/oauth2/jwks");
        String userInfoUri = valueOrDefault(provider.getUserInfoUri(), issuer + "/userinfo");
        String userNameAttribute = valueOrDefault(provider.getUserNameAttribute(), IdTokenClaimNames.SUB);

        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(registration.getClientId())
                .clientSecret(registration.getClientSecret())
                .clientAuthenticationMethod(new ClientAuthenticationMethod(
                        valueOrDefault(registration.getClientAuthenticationMethod(), "none")))
                .authorizationGrantType(new AuthorizationGrantType(
                        valueOrDefault(registration.getAuthorizationGrantType(), "authorization_code")))
                .redirectUri(registration.getRedirectUri())
                .scope(registration.getScope())
                .authorizationUri(authorizationUri)
                .tokenUri(tokenUri)
                .jwkSetUri(jwkSetUri)
                .issuerUri(issuer)
                .userInfoUri(userInfoUri)
                .userInfoAuthenticationMethod(AuthenticationMethod.HEADER)
                .userNameAttributeName(userNameAttribute)
                .providerConfigurationMetadata(Map.of(
                        "issuer", issuer,
                        "authorization_endpoint", authorizationUri,
                        "token_endpoint", tokenUri,
                        "jwks_uri", jwkSetUri,
                        "userinfo_endpoint", userInfoUri,
                        "end_session_endpoint", issuer + "/connect/logout"))
                .clientName(valueOrDefault(registration.getClientName(), registrationId))
                .build();
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
