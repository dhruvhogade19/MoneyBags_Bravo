package com.moneybags.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "eureka.client.enabled=false")
@ActiveProfiles("local")
class IdentityAccessServiceApplicationTests {
    @Test
    void contextLoads() {
    }
}
