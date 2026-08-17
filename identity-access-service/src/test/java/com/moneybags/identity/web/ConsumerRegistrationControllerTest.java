package com.moneybags.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moneybags.identity.user.BankUser;
import com.moneybags.identity.user.BankUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class ConsumerRegistrationControllerTest {

    @Test
    void createsAnUnlinkedConsumerWithANormalizedEmail() {
        BankUserRepository users = mock(BankUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("a-secure-password")).thenReturn("encoded");
        when(users.save(any(BankUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ConsumerRegistrationController controller = new ConsumerRegistrationController(users, encoder);

        var response = controller.register(new ConsumerRegistrationController.RegistrationRequest(
                "  New.Customer@MoneyBags.Local ", "a-secure-password"));

        ArgumentCaptor<BankUser> saved = ArgumentCaptor.forClass(BankUser.class);
        verify(users).save(saved.capture());
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().username()).isEqualTo("new.customer@moneybags.local");
        assertThat(saved.getValue().getCustomerId()).isNull();
        assertThat(saved.getValue().getRoles()).isEqualTo("CONSUMER");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded");
    }

    @Test
    void rejectsDuplicateEmailAddresses() {
        BankUserRepository users = mock(BankUserRepository.class);
        when(users.existsByUsernameIgnoreCase("existing@moneybags.local")).thenReturn(true);
        ConsumerRegistrationController controller = new ConsumerRegistrationController(
                users, mock(PasswordEncoder.class));

        assertThatThrownBy(() -> controller.register(new ConsumerRegistrationController.RegistrationRequest(
                "existing@moneybags.local", "a-secure-password")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }
}
