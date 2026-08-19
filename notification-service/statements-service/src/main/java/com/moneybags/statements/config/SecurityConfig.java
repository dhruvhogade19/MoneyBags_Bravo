package com.moneybags.statements.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain secured(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/internal/v1/**").hasAuthority("SCOPE_statements:service")
                .requestMatchers(HttpMethod.GET, "/api/v1/statements/**").hasAnyAuthority("SCOPE_statements:read", "SCOPE_statements:admin")
                .anyRequest().authenticated()).oauth2ResourceServer(o -> o.jwt(j -> {})).build();
    }
    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "false")
    SecurityFilterChain local(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }
}
