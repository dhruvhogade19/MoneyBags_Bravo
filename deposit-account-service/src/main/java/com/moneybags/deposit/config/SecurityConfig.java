package com.moneybags.deposit.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "true")
    SecurityFilterChain secured(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/deposit-accounts/fixed-deposits/**")
                            .hasAuthority("SCOPE_fd:read")
                        .requestMatchers(HttpMethod.GET, "/api/deposit-accounts/**").hasAuthority("SCOPE_account:read")
                        .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/fixed-deposits/*/premature-closure-quotes")
                            .hasAuthority("SCOPE_fd:read")
                        .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/fixed-deposits/*/premature-closure-requests")
                            .hasAuthority("SCOPE_fd:close")
                        .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/fixed-deposits/quotes")
                            .hasAuthority("SCOPE_fd:read")
                        .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/fixed-deposits")
                            .hasAuthority("SCOPE_fd:open")
                        .requestMatchers(HttpMethod.POST, "/api/deposit-accounts").hasAuthority("SCOPE_account:open")
                        .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/*/closure-quotes")
                            .hasAuthority("SCOPE_account:read")
                        .requestMatchers(HttpMethod.POST, "/api/deposit-accounts/*/closure-requests",
                                "/api/deposit-accounts/*/closure-requests/**")
                            .hasAuthority("SCOPE_account:close")
                        .requestMatchers("/api/internal/deposit-payment-operations/**")
                            .hasAuthority("SCOPE_deposit-payment:write")
                        .requestMatchers("/api/internal/**").hasAuthority("SCOPE_account:service")
                        .requestMatchers("/internal/v1/**").hasAuthority("SCOPE_account:service")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain localDevelopment(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
