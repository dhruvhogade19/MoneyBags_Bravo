package com.moneybags.identity.config;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IdentitySwitchLoginFilterTest {

    @Test
    void workspaceLoginDiscardsTheSharedCookieSessionAndResumesAfterAuthentication() throws Exception {
        IdentitySwitchLoginFilter filter = new IdentitySwitchLoginFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession previous = mock(HttpSession.class);
        HttpSession replacement = mock(HttpSession.class);
        Map<String, String[]> parameters = new LinkedHashMap<>();
        parameters.put("client_id", new String[] {"moneybags-admin"});
        parameters.put("mb_switch", new String[] {"1"});

        when(request.getRequestURI()).thenReturn("/oauth2/authorize");
        when(request.getParameter("mb_switch")).thenReturn("1");
        when(request.getParameterMap()).thenReturn(parameters);
        when(request.getSession(false)).thenReturn(previous);
        when(request.getSession(true)).thenReturn(replacement);
        when(request.getContextPath()).thenReturn("");

        filter.doFilterInternal(request, response, chain);

        verify(previous).invalidate();
        verify(replacement).setAttribute(
                eq(IdentitySwitchLoginFilter.RESUME_AUTHORIZATION_ATTRIBUTE),
                contains("client_id=moneybags-admin"));
        verify(response).sendRedirect("/login");
        verify(chain, never()).doFilter(request, response);
    }
}
