package com.moneybags.uibff.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneybags.uibff.auth.IdentityRegistrationClient;
import com.moneybags.uibff.auth.RegistrationController;
import com.moneybags.uibff.http.UpstreamResponse;
import com.moneybags.uibff.proxy.ApiProxyController;
import com.moneybags.uibff.proxy.AuthorizedSessionResolver;
import com.moneybags.uibff.proxy.CustomerKycAccessGuard;
import com.moneybags.uibff.proxy.GatewayProxyClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class SpaCsrfFlowTest {
    private IdentityRegistrationClient registrations;
    private AuthorizedSessionResolver sessions;
    private CustomerKycAccessGuard accessGuard;
    private GatewayProxyClient gateway;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registrations = mock(IdentityRegistrationClient.class);
        sessions = mock(AuthorizedSessionResolver.class);
        accessGuard = mock(CustomerKycAccessGuard.class);
        gateway = mock(GatewayProxyClient.class);

        CookieCsrfTokenRepository tokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfFilter filter = new CsrfFilter(tokens);
        filter.setRequestHandler(new SpaCsrfTokenRequestHandler());
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CsrfProbe(),
                        new RegistrationController(registrations),
                        new ApiProxyController(sessions, accessGuard, gateway),
                        new LogoutProbe())
                .addFilters(filter)
                .build();
    }

    @Test
    void acceptsThePlainCookieTokenForAnonymousRegistration() throws Exception {
        Cookie csrf = csrfCookie();
        when(registrations.register(any(), any())).thenReturn(new UpstreamResponse(
                HttpStatus.CREATED, new HttpHeaders(), "{}".getBytes()));

        mockMvc.perform(post("/api/registration")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@moneybags.local\",\"password\":\"StrongPassword!123\"}"))
                .andExpect(status().isCreated());

        verify(registrations).register(any(), any());
    }

    @Test
    void acceptsThePlainCookieTokenForAnAuthenticatedProxyMutation() throws Exception {
        Cookie csrf = csrfCookie();
        when(sessions.resolve(isNull(Authentication.class), any(), any())).thenReturn(
                new AuthorizedSessionResolver.AuthorizedSession(
                        "access-token", "moneybags", java.util.List.of("CONSUMER"), "101", null));
        when(gateway.authenticated(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new UpstreamResponse(HttpStatus.OK, new HttpHeaders(), "{}".getBytes()));

        mockMvc.perform(post("/api/proxy/api/v1/payments")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(gateway).authenticated(
                org.mockito.ArgumentMatchers.eq(HttpMethod.POST), any(), any(), any(), any(),
                any(), any(), any(), any(), isNull());
    }

    @Test
    void acceptsThePlainCookieTokenForLogout() throws Exception {
        Cookie csrf = csrfCookie();

        mockMvc.perform(post("/api/session/logout")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
    }

    private Cookie csrfCookie() throws Exception {
        Cookie csrf = mockMvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        if (csrf == null) throw new AssertionError("GET /api/session did not materialize XSRF-TOKEN");
        return csrf;
    }

    @RestController
    static class LogoutProbe {
        @PostMapping("/api/session/logout")
        org.springframework.http.ResponseEntity<Void> logout() {
            return org.springframework.http.ResponseEntity.noContent().build();
        }
    }

    @RestController
    static class CsrfProbe {
        @GetMapping("/api/session")
        java.util.Map<String, Boolean> session() {
            return java.util.Map.of("authenticated", false);
        }
    }
}
