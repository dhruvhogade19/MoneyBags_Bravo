package com.moneybags.deposit.config;

import java.util.ArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain secured(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("BANK_ADMIN")
                .requestMatchers("/api/internal/deposit-payment-operations/**",
                        "/internal/v1/deposit-payment-operations/**",
                        "/internal/v1/deposit-accounts/fixed-deposits/*/payout-confirmations")
                    .hasAuthority("SCOPE_deposit-payment:write")
                .requestMatchers("/api/internal/deposit-accounts/*/eligibility")
                    .hasAnyAuthority("SCOPE_deposit-payment:write", "SCOPE_account:service")
                .requestMatchers("/api/deposit-accounts/operations/**")
                    .hasAnyAuthority("SCOPE_account:admin", "SCOPE_fd:admin")
                .requestMatchers("/api/internal/**", "/internal/v1/**").hasAuthority("SCOPE_account:service")
                .requestMatchers(HttpMethod.GET, "/api/deposit-accounts/fixed-deposits/**")
                    .hasAnyAuthority("SCOPE_fd:read", "SCOPE_fd:admin")
                .requestMatchers(HttpMethod.GET, "/api/deposit-accounts/**")
                    .hasAnyAuthority("SCOPE_account:read", "SCOPE_account:admin")
                .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/fixed-deposits/*/premature-closure-quotes",
                        "/api/deposit-accounts/fixed-deposits/quotes")
                    .hasAnyAuthority("SCOPE_fd:read", "SCOPE_fd:admin")
                .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/fixed-deposits/*/premature-closure-requests")
                    .hasAnyAuthority("SCOPE_fd:close", "SCOPE_fd:admin")
                .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/fixed-deposits")
                    .hasAnyAuthority("SCOPE_fd:open", "SCOPE_fd:admin")
                // A customer may verify an active recipient before creating a payment.  This is
                // deliberately read-only and must be matched before the broader account-opening
                // POST rule below.
                .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/recipient-lookup")
                    .hasAnyAuthority("SCOPE_account:read", "SCOPE_account:admin")
                .requestMatchers(HttpMethod.POST, "/api/deposit-accounts", "/api/deposit-accounts/eligibility-check")
                    .hasAnyAuthority("SCOPE_account:open", "SCOPE_account:admin", "SCOPE_account:service")
                .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/*/closure-quotes")
                    .hasAnyAuthority("SCOPE_account:read", "SCOPE_account:admin")
                .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/*/closure-requests",
                        "/api/deposit-accounts/*/closure-requests/**")
                    .hasAnyAuthority("SCOPE_account:close", "SCOPE_account:admin")
                .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/*/holders",
                        "/api/deposit-accounts/*/mandates")
                    .hasAnyAuthority("SCOPE_account:write", "SCOPE_account:admin")
                .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/*/commands/*")
                    .hasAnyAuthority("SCOPE_account:admin", "SCOPE_account:service")
                .requestMatchers(HttpMethod.PUT, "/api/deposit-accounts/*/nominees",
                        "/api/deposit-accounts/*/limits/*")
                    .hasAnyAuthority("SCOPE_account:write", "SCOPE_account:admin")
                .requestMatchers(HttpMethod.DELETE, "/api/deposit-accounts/*/holders/*",
                        "/api/deposit-accounts/*/mandates/*")
                    .hasAnyAuthority("SCOPE_account:write", "SCOPE_account:admin")
                .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter())))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "false")
    SecurityFilterChain localDevelopment(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    private static Converter<Jwt, AbstractAuthenticationToken> converter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new ArrayList<>(scopes.convert(jwt));
            var roles = jwt.getClaimAsStringList("roles");
            if (roles != null) roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).forEach(authorities::add);
            return authorities;
        });
        return converter;
    }
}
