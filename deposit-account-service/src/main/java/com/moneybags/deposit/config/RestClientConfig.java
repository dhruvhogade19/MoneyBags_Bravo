package com.moneybags.deposit.config;

import org.slf4j.MDC;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.http.EurekaClientHttpRequestFactorySupplier;
import org.springframework.cloud.netflix.eureka.http.RestClientDiscoveryClientOptionalArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {
    /**
     * Keeps Eureka's registry traffic direct.  Without this override, Eureka's
     * RestClient picks up the application's {@link LoadBalanced} builder and
     * attempts to resolve "localhost" through Eureka before it has connected.
     */
    @Bean
    RestClientDiscoveryClientOptionalArgs eurekaRestClientOptionalArgs(
            EurekaClientHttpRequestFactorySupplier requestFactorySupplier) {
        return new RestClientDiscoveryClientOptionalArgs(requestFactorySupplier, RestClient::builder);
    }

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder(
            @Value("${moneybags.http.connect-timeout:2s}") Duration connectTimeout,
            @Value("${moneybags.http.read-timeout:5s}") Duration readTimeout,
            ClientCredentialsTokenProvider tokens) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).requestInterceptor((request, body, execution) -> {
            if (request.getHeaders().getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION) == null) {
                String token = tokens.token();
                if (token != null) request.getHeaders().setBearerAuth(token);
            }
            String correlationId = MDC.get("correlationId");
            if (correlationId != null && !correlationId.isBlank()) {
                request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
            }
            return execution.execute(request, body);
        });
    }
}
