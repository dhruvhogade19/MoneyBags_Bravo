package com.moneybags.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class TrustedIdentityHeadersFilterTest {

    @Test
    void configuresFrontendOriginForCorsResponses() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products")
                .header("Origin", "http://localhost:8000")
                .build());

        var configuration = new SecurityConfig().corsConfigurationSource("http://localhost:8000")
                .getCorsConfiguration(exchange);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:8000");
        assertThat(configuration.getAllowedMethods()).contains("GET", "POST", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactly("*");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    @Test
    void allowsCorsPreflightWithoutJwtOrTrustedIdentityHeaders() {
        var request = MockServerHttpRequest.options("/api/deposit-accounts")
                .header("Origin", "http://localhost:8000")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,x-tenant-id,x-correlation-id")
                .build();
        var exchange = MockServerWebExchange.from(request);
        var invoked = new AtomicBoolean();

        new TrustedIdentityHeadersFilter().filter(exchange, current -> {
            invoked.set(true);
            return Mono.empty();
        }).block();

        assertThat(invoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
