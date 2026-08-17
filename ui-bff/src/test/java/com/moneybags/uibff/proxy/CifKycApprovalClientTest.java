package com.moneybags.uibff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moneybags.uibff.api.BffApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class CifKycApprovalClientTest {
    @Test
    void readsApprovalFromTheAuthoritativeCifUsingTrustedSessionHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CifKycApprovalClient client = new CifKycApprovalClient(
                builder.baseUrl("http://gateway").build(), new ObjectMapper());
        server.expect(requestTo("http://gateway/api/v1/cifs/101"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(header("X-Tenant-ID", "moneybags"))
                .andExpect(header("X-Correlation-ID", "correlation-1"))
                .andRespond(withSuccess("{\"cifId\":101,\"kycStatus\":\"APPROVED\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.isApproved(customer(), "correlation-1")).isTrue();
        server.verify();
    }

    @Test
    void returnsFalseForANonApprovedCif() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CifKycApprovalClient client = new CifKycApprovalClient(
                builder.baseUrl("http://gateway").build(), new ObjectMapper());
        server.expect(requestTo("http://gateway/api/v1/cifs/101"))
                .andRespond(withSuccess("{\"kycStatus\":\"PENDING\"}", MediaType.APPLICATION_JSON));

        assertThat(client.isApproved(customer(), "correlation-1")).isFalse();
        server.verify();
    }

    @Test
    void deniesWhenTheAuthoritativeResponseCannotBeTrusted() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CifKycApprovalClient client = new CifKycApprovalClient(
                builder.baseUrl("http://gateway").build(), new ObjectMapper());
        server.expect(requestTo("http://gateway/api/v1/cifs/101"))
                .andRespond(withSuccess("not-json", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.isApproved(customer(), "correlation-1"))
                .isInstanceOfSatisfying(BffApiException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasMessageContaining("could not be verified");
        server.verify();
    }

    private static AuthorizedSessionResolver.AuthorizedSession customer() {
        return new AuthorizedSessionResolver.AuthorizedSession(
                "access-token", "moneybags", List.of("CONSUMER"), "101", null);
    }
}
