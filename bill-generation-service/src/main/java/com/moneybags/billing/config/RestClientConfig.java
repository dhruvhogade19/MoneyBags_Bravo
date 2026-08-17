package com.moneybags.billing.config;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.http.EurekaClientHttpRequestFactorySupplier;
import org.springframework.cloud.netflix.eureka.http.RestClientDiscoveryClientOptionalArgs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "moneybags.billing.stub-upstream-clients", havingValue = "false")
public class RestClientConfig {
    private final ClientCredentialsTokenProvider tokens;
    public RestClientConfig(ClientCredentialsTokenProvider tokens) { this.tokens = tokens; }

    @Bean
    RestClientDiscoveryClientOptionalArgs eurekaRestClientOptionalArgs(
            EurekaClientHttpRequestFactorySupplier requestFactorySupplier) {
        return new RestClientDiscoveryClientOptionalArgs(requestFactorySupplier, RestClient::builder);
    }

    @Bean
    @LoadBalanced
    RestClient.Builder billingRestClientBuilder(
            @Value("${moneybags.http.connect-timeout:3s}") Duration connectTimeout,
            @Value("${moneybags.http.read-timeout:8s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestInterceptor((request, body, execution) -> {
                    if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                        String token = tokens.token();
                        if (token != null) request.getHeaders().setBearerAuth(token);
                    }
                    if (request.getHeaders().getFirst("X-Correlation-Id") == null) {
                        request.getHeaders().set("X-Correlation-Id",
                                java.util.Optional.ofNullable(MDC.get("correlationId"))
                                        .filter(value -> !value.isBlank())
                                        .orElseGet(() -> UUID.randomUUID().toString()));
                    }
                    return execution.execute(request, body);
                });
    }

    @Bean @Qualifier("billingProductRestClient") RestClient product(RestClient.Builder builder, @Value("${moneybags.billing.product-base-url}") String url) { return builder.clone().baseUrl(url).build(); }
    @Bean @Qualifier("billingCreditCardRestClient") RestClient creditCard(RestClient.Builder builder, @Value("${moneybags.billing.credit-card-base-url}") String url) { return builder.clone().baseUrl(url).build(); }
    @Bean @Qualifier("billingAccountingRestClient") RestClient accounting(RestClient.Builder builder, @Value("${moneybags.billing.accounting-base-url}") String url) { return builder.clone().baseUrl(url).build(); }
    @Bean @Qualifier("billingNotificationRestClient") RestClient notification(RestClient.Builder builder, @Value("${moneybags.billing.notification-base-url}") String url) { return builder.clone().baseUrl(url).build(); }
}
