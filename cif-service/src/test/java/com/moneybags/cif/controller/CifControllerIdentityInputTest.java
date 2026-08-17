package com.moneybags.cif.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moneybags.cif.domain.enums.EmploymentType;
import com.moneybags.cif.dto.request.CreateCifRequest;
import com.moneybags.cif.service.CifService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CifControllerIdentityInputTest {
    @Test
    void takesCustomerEmailFromTheSignedInIdentityWhenTheFormOmitsIt() {
        var service = org.mockito.Mockito.mock(CifService.class);
        var controller = new CifController(service);
        var request = new CreateCifRequest(
                "Test", "Customer", LocalDate.of(1995, 5, 20), null, null, "9000000000",
                "Test address", EmploymentType.SALARIED, new BigDecimal("50000"),
                "ABCDE1234F", "123456789012");
        var token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("tenant_id", "moneybags")
                .claim("user_id", "identity-user")
                .claim("preferred_username", "customer@example.com")
                .build();
        var authentication = new JwtAuthenticationToken(token,
                List.of(new SimpleGrantedAuthority("ROLE_CONSUMER")));

        when(service.createCif(any(), any(), any())).thenReturn(null);
        controller.createCif(request, authentication);

        var requestCaptor = ArgumentCaptor.forClass(CreateCifRequest.class);
        verify(service).createCif(requestCaptor.capture(), org.mockito.ArgumentMatchers.eq("identity-user"),
                org.mockito.ArgumentMatchers.eq("moneybags"));
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().email())
                .isEqualTo("customer@example.com");
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().age()).isNull();
    }
}
