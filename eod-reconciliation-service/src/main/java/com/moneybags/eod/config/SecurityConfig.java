package com.moneybags.eod.config;

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
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/business-date").hasAuthority("SCOPE_eod:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/eod/**").hasAuthority("SCOPE_eod:read")
                        .requestMatchers("/api/v1/eod/**", "/api/v1/business-date/**").hasAuthority("SCOPE_eod:admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain local(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
}
