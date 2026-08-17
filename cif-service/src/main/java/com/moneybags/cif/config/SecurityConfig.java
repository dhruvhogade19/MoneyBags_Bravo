package com.moneybags.cif.config;

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
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .hasRole("BANK_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/cifs/*/kyc-status")
                            .hasAuthority("SCOPE_cif:service")
                        .requestMatchers(HttpMethod.GET, "/api/v1/cifs/*/credit-card-details",
                                "/api/v1/cifs/*/deposit-creation-details",
                                "/api/v1/cifs/*/customer-contact-details")
                            .hasAuthority("SCOPE_cif:service")
                        .requestMatchers(HttpMethod.POST, "/api/v1/cifs")
                            .hasAnyAuthority("ROLE_BANK_ADMIN", "SCOPE_cif:write", "SCOPE_cif:service")
                        .requestMatchers(HttpMethod.POST, "/api/v1/cifs/me/identity-link")
                            .hasAuthority("SCOPE_cif:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/cifs/**")
                            .hasAnyAuthority("SCOPE_cif:read", "SCOPE_cif:admin")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/cifs/**")
                            .hasAnyAuthority("SCOPE_cif:write", "SCOPE_cif:admin")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "false")
    SecurityFilterChain local(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new ArrayList<>(scopes.convert(jwt));
            var roles = jwt.getClaimAsStringList("roles");
            if (roles != null) roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .forEach(authorities::add);
            return authorities;
        });
        return converter;
    }
}
