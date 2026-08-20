package com.moneybags.accounting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "moneybags.security.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountingInternalSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void internalAccountingPeriodReadRequiresAccountingServiceScope() throws Exception {
        String path = "/internal/v1/accounting-periods/2194-01-01";

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(jwt().authorities(
                        new SimpleGrantedAuthority("SCOPE_accounting:read"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().authorities(
                        new SimpleGrantedAuthority("SCOPE_accounting:service"))))
                .andExpect(status().isNotFound());
    }
}
