package com.moneybags.productmaster.config;

import java.util.ArrayList;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "true")
    SecurityFilterChain secured(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("BANK_ADMIN")
                        .requestMatchers("/internal/v1/products/**").hasAuthority("SCOPE_product:validate")
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/v1/products/**",
                                "/api/benchmarks/**").hasAuthority("SCOPE_product:read")
                        .requestMatchers("/api/products/**", "/api/v1/products/**",
                                "/api/benchmarks/**")
                        // A bank administrator is the business authority for catalogue changes.  Keep the
                        // product:admin scope for service/client tokens, but do not reject a valid admin
                        // session merely because it was issued before that scope was added to the client.
                        .hasAnyAuthority("SCOPE_product:admin", "ROLE_BANK_ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter())))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain localDevelopment(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
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
