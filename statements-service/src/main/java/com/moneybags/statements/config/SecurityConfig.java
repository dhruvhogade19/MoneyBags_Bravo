package com.moneybags.statements.config;

import java.util.ArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain secured(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/v3/api-docs/**",
                        "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/internal/v1/**").hasAuthority("SCOPE_statements:service")
                .requestMatchers(HttpMethod.POST, "/api/v1/statements")
                    .hasAnyAuthority("SCOPE_statements:generate", "SCOPE_statements:admin")
                .requestMatchers(HttpMethod.GET, "/api/v1/statements/**")
                    .hasAnyAuthority("SCOPE_statements:read", "SCOPE_statements:admin")
                .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(converter())))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "false")
    SecurityFilterChain local(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    private static Converter<Jwt, AbstractAuthenticationToken> converter() {
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
