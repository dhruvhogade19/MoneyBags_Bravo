package com.moneybags.billing.config;

import java.time.Duration;
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
    /** Keep Eureka registry traffic direct instead of attempting to resolve localhost through Eureka. */
    @Bean
    RestClientDiscoveryClientOptionalArgs eurekaRestClientOptionalArgs(
            EurekaClientHttpRequestFactorySupplier requestFactorySupplier) {
        return new RestClientDiscoveryClientOptionalArgs(requestFactorySupplier, RestClient::builder);
    }

    /**
     * Every billing peer URL uses a Eureka service name.  Building clients from
     * this annotated builder is therefore required; a plain RestClient attempts
     * DNS resolution for names such as credit-card-service and fails locally.
     */
    @Bean
    @LoadBalanced
    RestClient.Builder billingLoadBalancedRestClientBuilder(ClientCredentialsTokenProvider tokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestInterceptor((request, body, execution) -> {
                    if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                        request.getHeaders().setBearerAuth(tokens.token());
                    }
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null && !correlationId.isBlank()) {
                        request.getHeaders().set("X-Correlation-ID", correlationId);
                    }
                    return execution.execute(request, body);
                });
    }

    @Bean @Qualifier("billingProductRestClient")
    RestClient product(@Qualifier("billingLoadBalancedRestClientBuilder") RestClient.Builder billingLoadBalancedRestClientBuilder,
                       @Value("${moneybags.billing.product-base-url}") String url) {
        return client(billingLoadBalancedRestClientBuilder, url);
    }

    @Bean @Qualifier("billingCreditCardRestClient")
    RestClient creditCard(@Qualifier("billingLoadBalancedRestClientBuilder") RestClient.Builder billingLoadBalancedRestClientBuilder,
                          @Value("${moneybags.billing.credit-card-base-url}") String url) {
        return client(billingLoadBalancedRestClientBuilder, url);
    }

    @Bean @Qualifier("billingAccountingRestClient")
    RestClient accounting(@Qualifier("billingLoadBalancedRestClientBuilder") RestClient.Builder billingLoadBalancedRestClientBuilder,
                          @Value("${moneybags.billing.accounting-base-url}") String url) {
        return client(billingLoadBalancedRestClientBuilder, url);
    }

    @Bean @Qualifier("billingNotificationRestClient")
    RestClient notification(@Qualifier("billingLoadBalancedRestClientBuilder") RestClient.Builder billingLoadBalancedRestClientBuilder,
                            @Value("${moneybags.billing.notification-base-url}") String url) {
        return client(billingLoadBalancedRestClientBuilder, url);
    }

    private RestClient client(RestClient.Builder builder, String url) {
        return builder.clone().baseUrl(url).build();
    }
}
