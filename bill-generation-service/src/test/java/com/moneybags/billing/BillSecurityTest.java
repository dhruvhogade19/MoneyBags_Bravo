package com.moneybags.billing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

@SpringBootTest(properties = "moneybags.security.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void identityClaimsMapToBillingAdminAuthorities() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(300),
                java.util.Map.of("alg", "none"),
                java.util.Map.of("sub", "admin", "scope", List.of("billing:read", "billing:admin"),
                        "roles", List.of("BANK_ADMIN")));

        var authentication = BillGenerationApplication.Security.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities()).extracting(Object::toString)
                .contains("SCOPE_billing:admin", "ROLE_BANK_ADMIN");
    }

    @Test
    void billingAdminScopeCanPostToExactAdminCollectionEndpoint() throws Exception {
        mvc.perform(post("/api/v1/bills/admin")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_billing:admin")))
                        .header("Idempotency-Key", "security-regression-admin-generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cifId": 101,
                                  "accountId": "77777777-7777-7777-7777-777777777777",
                                  "startDate": "2026-04-01",
                                  "endDate": "2026-04-30"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void customerScopeCannotPostToAdminCollectionEndpoint() throws Exception {
        mvc.perform(post("/api/v1/bills/admin")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_billing:read")))
                        .header("Idempotency-Key", "security-regression-customer-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cifId": 101,
                                  "accountId": "88888888-8888-8888-8888-888888888888",
                                  "startDate": "2026-03-01",
                                  "endDate": "2026-03-31"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminValidationFailureIsNotMaskedAsForbidden() throws Exception {
        mvc.perform(post("/api/v1/bills/admin")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_billing:admin")))
                        .header("Idempotency-Key", "security-regression-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
