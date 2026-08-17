package com.moneybags.uibff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GatewayProxyClientTest {
    @Test
    void injectsTrustedHeadersAndDoesNotForwardBrowserIdentityHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayProxyClient client = new GatewayProxyClient(builder.baseUrl("http://gateway").build());
        server.expect(requestTo("http://gateway/api/v1/payments?size=5"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(header("X-Tenant-ID", "moneybags"))
                .andExpect(header("X-Correlation-ID", "aa29f73b-8c78-4cbb-9b91-7d9a97380678"))
                .andExpect(header("Idempotency-Key", "payment-1"))
                .andExpect(header("X-Actor-Id", "admin-user-7"))
                .andExpect(request -> {
                    assertThat(request.getHeaders().containsHeader(HttpHeaders.COOKIE)).isFalse();
                    assertThat(request.getHeaders().containsHeader("X-Customer-ID")).isFalse();
                    assertThat(request.getHeaders().containsHeader("X-Authenticated-User")).isFalse();
                    assertThat(request.getHeaders().getValuesAsList("X-Actor-Id"))
                            .doesNotContain("forged-browser-actor");
                })
                .andRespond(withSuccess("{\"status\":\"BOOKED\"}", MediaType.APPLICATION_JSON));

        HttpHeaders browserHeaders = new HttpHeaders();
        browserHeaders.setContentType(MediaType.APPLICATION_JSON);
        browserHeaders.set(HttpHeaders.COOKIE, "JSESSIONID=browser-only");
        browserHeaders.set("X-Customer-ID", "forged");
        browserHeaders.set("X-Actor-Id", "forged-browser-actor");
        var response = client.authenticated(HttpMethod.POST, "/api/v1/payments", "size=5",
                browserHeaders, "{}".getBytes(StandardCharsets.UTF_8), "access-token", "moneybags",
                "aa29f73b-8c78-4cbb-9b91-7d9a97380678", "payment-1", "admin-user-7");

        assertThat(response.status().value()).isEqualTo(200);
        assertThat(response.body()).asString(StandardCharsets.UTF_8).contains("BOOKED");
        server.verify();
    }

    @Test
    void doesNotSendAnActorHeaderForCustomerCalls() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayProxyClient client = new GatewayProxyClient(builder.baseUrl("http://gateway").build());
        server.expect(requestTo("http://gateway/api/deposit-accounts"))
                .andExpect(request -> assertThat(
                        request.getHeaders().containsHeader("X-Actor-Id")).isFalse())
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        HttpHeaders browserHeaders = new HttpHeaders();
        browserHeaders.set("X-Actor-Id", "forged-browser-actor");
        client.authenticated(HttpMethod.GET, "/api/deposit-accounts", null,
                browserHeaders, null, "access-token", "moneybags",
                "aa29f73b-8c78-4cbb-9b91-7d9a97380678", null, null);

        server.verify();
    }

    @Test
    void publicProductRequestCarriesNoBearerToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayProxyClient client = new GatewayProxyClient(builder.baseUrl("http://gateway").build());
        server.expect(requestTo("http://gateway/api/products/active"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(
                        request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse())
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.publicGet("/api/products/active", null, new HttpHeaders());

        server.verify();
    }
}
