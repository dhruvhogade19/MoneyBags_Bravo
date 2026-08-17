package com.moneybags.billing.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "moneybags.billing.stub-upstream-clients", havingValue = "false")
public class RestClientConfig {
    private final ClientCredentialsTokenProvider tokens;
    public RestClientConfig(ClientCredentialsTokenProvider tokens) { this.tokens = tokens; }
    @Bean @Qualifier("billingProductRestClient") RestClient product(@Value("${moneybags.billing.product-base-url}") String url) { return client(url); }
    @Bean @Qualifier("billingCreditCardRestClient") RestClient creditCard(@Value("${moneybags.billing.credit-card-base-url}") String url) { return client(url); }
    @Bean @Qualifier("billingAccountingRestClient") RestClient accounting(@Value("${moneybags.billing.accounting-base-url}") String url) { return client(url); }
    @Bean @Qualifier("billingNotificationRestClient") RestClient notification(@Value("${moneybags.billing.notification-base-url}") String url) { return client(url); }
    private RestClient client(String url) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("Accept", "application/json")
                .requestInterceptor((request, body, execution) -> { if (request.getHeaders().getFirst("Authorization") == null) request.getHeaders().setBearerAuth(tokens.token()); return execution.execute(request, body); }).build();
    }
}
