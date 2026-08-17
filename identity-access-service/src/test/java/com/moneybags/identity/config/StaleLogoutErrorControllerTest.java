package com.moneybags.identity.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StaleLogoutErrorControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StaleLogoutErrorController(
                "http://localhost:8000/login/oauth2/code/moneybags-consumer",
                "http://localhost:8000/login/oauth2/code/moneybags-admin")).build();
    }

    @Test
    void redirectsAStaleLogoutOnlyToARegisteredLocalReturnUri() throws Exception {
        mvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/connect/logout")
                        .param("post_logout_redirect_uri", "http://localhost:8000/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:8000/"));
    }

    @Test
    void rejectsAnUnregisteredLogoutRedirect() throws Exception {
        mvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/connect/logout")
                        .param("post_logout_redirect_uri", "https://attacker.example/"))
                .andExpect(status().isBadRequest());
    }
}
