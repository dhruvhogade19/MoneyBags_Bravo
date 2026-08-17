package com.moneybags.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ConsumerRegistrationControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    BankUserRepository repository;

    @Test
    void registersAnEnabledConsumerWithoutGrantingACustomerIdentityYet() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@moneybags.local";
        String request = "{\"email\":\"" + email.toUpperCase() + "\","
                + "\"password\":\"StrongPassword!123\"}";

        mockMvc.perform(post("/api/v1/identity/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "/api/v1/identity/users/")))
                .andExpect(jsonPath("$.username").value(email))
                .andExpect(jsonPath("$.onboardingStatus").value("PENDING_PROFILE"));

        BankUser saved = repository.findByUsernameIgnoreCase(email).orElseThrow();
        assertThat(saved.getRoles()).isEqualTo("CONSUMER");
        assertThat(saved.getCustomerId()).isNull();
        assertThat(saved.getTenantId()).isEqualTo("moneybags");
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void returnsConflictWithoutReplacingAnExistingIdentity() throws Exception {
        String request = """
                {"email":"customer@moneybags.local","password":"StrongPassword!123"}
                """;

        mockMvc.perform(post("/api/v1/identity/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());

        assertThat(repository.findByUsernameIgnoreCase("customer@moneybags.local")).isPresent();
    }
}
