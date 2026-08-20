package com.moneybags.identity.config;

import com.moneybags.identity.user.BankPrincipal;
import com.moneybags.identity.user.BankUser;
import com.moneybags.identity.user.BankUserRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class AuthorizationServerConfig {

    private static final Set<String> CONSUMER_SCOPES = Set.of(
            "openid", "profile", "product:read", "cif:read", "cif:write", "kyc:read", "kyc:write",
            "account:read", "account:open", "account:write",
            "account:close", "fd:read", "fd:open", "fd:close", "payment:read", "payment:write",
            "card:read", "card:apply", "billing:read", "notification:read",
            "statements:read", "statements:generate");

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                new OAuth2AuthorizationServerConfigurer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .cors(Customizer.withDefaults())
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(resourceServerJwtAuthenticationConverter())))
                .addFilterBefore(new IdentitySwitchLoginFilter(), SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/identity/registrations"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/session/logout", "/styles/**", "/actuator/health/**", "/error",
                                "/api/v1/identity/registrations").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll()
                        .successHandler((request, response, authentication) -> {
                            HttpSession session = request.getSession(false);
                            String resumeAuthorization = session == null ? null
                                    : (String) session.getAttribute(IdentitySwitchLoginFilter.RESUME_AUTHORIZATION_ATTRIBUTE);
                            if (resumeAuthorization != null) {
                                session.removeAttribute(IdentitySwitchLoginFilter.RESUME_AUTHORIZATION_ATTRIBUTE);
                                response.sendRedirect(resumeAuthorization);
                                return;
                            }
                            new SavedRequestAwareAuthenticationSuccessHandler()
                                    .onAuthenticationSuccess(request, response, authentication);
                        }))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(resourceServerJwtAuthenticationConverter())))
                .build();
    }

    static JwtAuthenticationConverter resourceServerJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new ArrayList<>(scopes.convert(jwt));
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .forEach(authorities::add);
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${moneybags.identity.cors-allowed-origin:http://localhost:8000}") String allowedOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(
            PasswordEncoder encoder,
            @Value("${moneybags.identity.clients.consumer.redirect-uri}") String consumerRedirect,
            @Value("${moneybags.identity.clients.admin.redirect-uri}") String adminRedirect,
            @Value("${moneybags.identity.clients.service-secret}") String serviceSecret) {
        if (serviceSecret == null || serviceSecret.isBlank()) {
            throw new IllegalStateException("M2M_CLIENT_SECRET is required outside the local profile");
        }
        TokenSettings humanTokens = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(10))
                .refreshTokenTimeToLive(Duration.ofHours(8))
                .reuseRefreshTokens(false)
                .build();
        TokenSettings serviceTokens = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .build();

        RegisteredClient consumer = publicClient("moneybags-consumer", consumerRedirect, humanTokens,
                CONSUMER_SCOPES);
        RegisteredClient admin = publicClient("moneybags-admin", adminRedirect, humanTokens,
                Set.of("openid", "profile", "product:read", "product:admin", "cif:read", "cif:admin",
                        "kyc:read", "kyc:review", "account:read",
                        "account:admin", "fd:read", "fd:admin", "payment:read", "payment:admin", "card:read",
                        "card:admin", "accounting:read", "accounting:admin", "notification:read",
                        "notification:admin", "billing:read", "billing:admin",
                        "statements:read", "statements:admin"));

        List<RegisteredClient> clients = new java.util.ArrayList<>();
        clients.add(consumer);
        clients.add(admin);
        clients.add(serviceClient("payments-service", serviceSecret, encoder, serviceTokens,
                "deposit-payment:write", "card-payment:write", "accounting:service", "notification:service", "billing:service"));
        clients.add(serviceClient("deposit-account-service", serviceSecret, encoder, serviceTokens,
                "cif:service", "product:read", "product:validate", "accounting:service", "notification:service"));
        clients.add(serviceClient("credit-card-service", serviceSecret, encoder, serviceTokens,
                "cif:service", "product:validate", "accounting:service", "notification:service"));
        clients.add(serviceClient("kyc-service", serviceSecret, encoder, serviceTokens,
                "cif:service", "notification:service"));
        clients.add(serviceClient("notification-service", serviceSecret, encoder, serviceTokens, "cif:service"));
        clients.add(serviceClient("cif-service", serviceSecret, encoder, serviceTokens,
                "kyc:service", "identity:service"));
        clients.add(serviceClient("bill-generation-service", serviceSecret, encoder, serviceTokens,
                "product:validate", "card:billing", "accounting:service", "notification:service"));
        clients.add(serviceClient("statements-service", serviceSecret, encoder, serviceTokens,
                "account:service", "accounting:service"));
        clients.add(serviceClient("eod-reconciliation-service", serviceSecret, encoder, serviceTokens,
                "account:service", "accounting:service", "billing:service", "payment:service",
                "statements:service", "notification:service", "card:eod"));
        return new InMemoryRegisteredClientRepository(clients);
    }

    private static RegisteredClient publicClient(String clientId, String redirectUri, TokenSettings tokens,
                                                 Set<String> scopes) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri)
                .postLogoutRedirectUri(redirectUri)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .tokenSettings(tokens);
        scopes.forEach(builder::scope);
        return builder.build();
    }

    private static RegisteredClient serviceClient(String clientId, String secret, PasswordEncoder encoder,
                                                   TokenSettings tokens,
                                                   String... scopes) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(encoder.encode(secret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenSettings(tokens);
        for (String scope : scopes) builder.scope(scope);
        return builder.build();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(BankUserRepository users) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) return;
            Object principal = context.getPrincipal().getPrincipal();
            if (principal instanceof BankPrincipal user) {
                ResolvedBankIdentity identity = resolveBankIdentity(user, users);
                context.getClaims().claim("roles", identity.roles())
                        .claim("user_id", user.userId())
                        .claim("tenant_id", identity.tenantId());
                if (identity.customerId() != null) context.getClaims().claim("customer_id", identity.customerId());
                if (identity.roles().contains("CONSUMER") && !identity.roles().contains("BANK_ADMIN")) {
                    Set<String> safeScopes = new LinkedHashSet<>(context.getAuthorizedScopes());
                    safeScopes.retainAll(CONSUMER_SCOPES);
                    context.getClaims().claim("scope", safeScopes);
                }
            } else {
                context.getClaims().claim("roles", List.of())
                        .claim("user_id", context.getRegisteredClient().getClientId())
                        .claim("tenant_id", "system");
            }
            context.getClaims().audience(List.of("moneybags-api"));
        };
    }

    static ResolvedBankIdentity resolveBankIdentity(BankPrincipal user, BankUserRepository users) {
        BankUser current = users.findById(user.userId()).orElse(null);
        if (current == null) {
            List<String> roles = user.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .map(authority -> authority.substring(5))
                    .toList();
            return new ResolvedBankIdentity(roles, user.tenantId(), user.customerId());
        }
        List<String> roles = Arrays.stream(current.getRoles().split("[,\\s]+"))
                .filter(role -> !role.isBlank())
                .toList();
        return new ResolvedBankIdentity(roles, current.getTenantId(), current.getCustomerId());
    }

    record ResolvedBankIdentity(List<String> roles, String tenantId, String customerId) {}

    @Bean
    AuthorizationServerSettings authorizationServerSettings(
            @Value("${moneybags.identity.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    @Profile("local | test")
    JWKSource<SecurityContext> localJwkSource() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair pair = generator.generateKeyPair();
        return jwkSource((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
    }

    @Bean
    @Profile("!local & !test")
    JWKSource<SecurityContext> productionJwkSource(
            @Value("${IDENTITY_JWK_PUBLIC_KEY:}") String publicKey,
            @Value("${IDENTITY_JWK_PRIVATE_KEY:}") String privateKey) throws Exception {
        if (publicKey.isBlank() || privateKey.isBlank()) {
            throw new IllegalStateException("IDENTITY_JWK_PUBLIC_KEY and IDENTITY_JWK_PRIVATE_KEY are required");
        }
        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPublicKey rsaPublic = (RSAPublicKey) factory.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(cleanPem(publicKey))));
        RSAPrivateKey rsaPrivate = (RSAPrivateKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(cleanPem(privateKey))));
        return jwkSource(rsaPublic, rsaPrivate);
    }

    private static JWKSource<SecurityContext> jwkSource(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        RSAKey rsa = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(UUID.randomUUID().toString()).build();
        JWKSet set = new JWKSet(rsa);
        return (selector, context) -> selector.select(set);
    }

    private static String cleanPem(String value) {
        return value.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}
