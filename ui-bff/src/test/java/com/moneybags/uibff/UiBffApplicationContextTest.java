package com.moneybags.uibff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@SpringBootTest
class UiBffApplicationContextTest {
    @Autowired
    Environment environment;

    @Autowired
    ClientRegistrationRepository registrations;

    @Test
    void contextLoads() {
    }

    @Test
    void usesADedicatedBrowserSessionCookie() {
        assertThat(environment.getProperty("server.servlet.session.cookie.name"))
                .isEqualTo("MONEYBAGS_UI_SESSION");
    }

    @Test
    void buildsOidcClientsWithoutCallingIdentityDuringStartup() {
        var admin = registrations.findByRegistrationId("moneybags-admin");
        assertThat(admin).isNotNull();
        assertThat(admin.getProviderDetails().getIssuerUri()).isEqualTo("http://localhost:8093");
        assertThat(admin.getProviderDetails().getAuthorizationUri())
                .isEqualTo("http://localhost:8093/oauth2/authorize");
        assertThat(admin.getProviderDetails().getJwkSetUri())
                .isEqualTo("http://localhost:8093/oauth2/jwks");
        assertThat(admin.getProviderDetails().getConfigurationMetadata())
                .containsEntry("end_session_endpoint", "http://localhost:8093/connect/logout");
    }
}
