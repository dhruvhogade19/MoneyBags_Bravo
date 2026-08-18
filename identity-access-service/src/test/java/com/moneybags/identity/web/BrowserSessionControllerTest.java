package com.moneybags.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BrowserSessionControllerTest {

    @Test
    void logoutInvalidatesTheIdentitySessionAndReturnsToTheFrontend() throws Exception {
        BrowserSessionController controller = new BrowserSessionController("http://localhost:8000");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.isSecure()).thenReturn(false);

        controller.logout(request, response);

        verify(session).invalidate();
        ArgumentCaptor<Cookie> cookie = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookie.capture());
        assertThat(cookie.getValue().getName()).isEqualTo("JSESSIONID");
        assertThat(cookie.getValue().getMaxAge()).isZero();
        verify(response).sendRedirect("http://localhost:8000");
    }
}
