package com.moneybags.creditcard.config;

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
                .requestMatchers(HttpMethod.GET, "/internal/v1/credit-card-accounts/*/billing-details")
                    .hasAuthority("SCOPE_card:billing")
                .requestMatchers(HttpMethod.POST, "/api/credit-cards/applications").hasAuthority("SCOPE_card:apply")
                .requestMatchers(HttpMethod.GET, "/api/credit-cards/accounts/eod/readiness")
                    .hasAnyAuthority("SCOPE_card:admin", "SCOPE_card-payment:write")
                .requestMatchers(HttpMethod.GET, "/api/credit-cards/applications")
                    .hasAuthority("SCOPE_card:admin")
                .requestMatchers(HttpMethod.GET, "/api/credit-cards/applications/**", "/api/credit-cards/accounts/**")
                    .hasAnyAuthority("SCOPE_card:read", "SCOPE_card:admin", "SCOPE_card-payment:write")
                .requestMatchers(HttpMethod.POST, "/api/credit-cards/applications/*/approve",
                        "/api/credit-cards/applications/*/reject", "/api/credit-cards/accounts")
                    .hasAuthority("SCOPE_card:admin")
                .requestMatchers(HttpMethod.POST, "/api/credit-cards/accounts/*/holds/**",
                        "/api/credit-cards/accounts/*/payments/billpaid")
                    .hasAuthority("SCOPE_card-payment:write")
                .requestMatchers(HttpMethod.POST, "/api/credit-cards/accounts/*/close")
                    .hasAnyAuthority("SCOPE_card:read", "SCOPE_card:admin")
                .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter())))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "false")
    SecurityFilterChain local(HttpSecurity http) throws Exception {
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
