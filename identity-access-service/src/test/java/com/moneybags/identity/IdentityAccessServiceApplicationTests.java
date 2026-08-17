package com.moneybags.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "eureka.client.enabled=false")
@ActiveProfiles("local")
class IdentityAccessServiceApplicationTests {
    @Autowired
    Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    void usesADedicatedIdentitySessionCookie() {
        assertThat(environment.getProperty("server.servlet.session.cookie.name"))
                .isEqualTo("MONEYBAGS_IDP_SESSION");
    }
}
