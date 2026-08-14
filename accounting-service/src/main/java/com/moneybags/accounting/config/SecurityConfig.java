package com.moneybags.accounting.config;

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
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain secured(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers("/internal/v1/**").hasAuthority("SCOPE_accounting:service")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyAuthority(
                                "SCOPE_accounting:read", "SCOPE_accounting:admin")
                        .requestMatchers("/api/v1/**").hasAuthority("SCOPE_accounting:admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "false")
    SecurityFilterChain localDevelopment(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
}
