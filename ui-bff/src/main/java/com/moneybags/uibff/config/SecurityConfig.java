package com.moneybags.uibff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ClientRegistrationRepository registrations) throws Exception {
        var logoutHandler = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logoutHandler.setPostLogoutRedirectUri("{baseUrl}/");

        return http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/session", "/api/session/me",
                                "/api/session/login-links").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/registration", "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/products", "/api/public/products/**").permitAll()
                        .requestMatchers("/api/proxy/**", "/api/session/logout").authenticated()
                        .requestMatchers("/api/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout.logoutUrl("/api/session/logout")
                        .logoutSuccessHandler(logoutHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.pathPattern("/api/**")))
                .build();
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientRepository authorizedClients) {
        var manager = new DefaultOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build());
        return manager;
    }
}
