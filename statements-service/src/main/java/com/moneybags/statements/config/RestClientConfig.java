package com.moneybags.statements.config;

import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.http.EurekaClientHttpRequestFactorySupplier;
import org.springframework.cloud.netflix.eureka.http.RestClientDiscoveryClientOptionalArgs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean
    RestClientDiscoveryClientOptionalArgs eurekaRestClientOptionalArgs(
            EurekaClientHttpRequestFactorySupplier requestFactorySupplier) {
        return new RestClientDiscoveryClientOptionalArgs(requestFactorySupplier, RestClient::builder);
    }

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder(ClientCredentialsTokenProvider tokens) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                        String token = tokens.token();
                        if (token != null) request.getHeaders().setBearerAuth(token);
                    }
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null) {
                        request.getHeaders().set("X-Correlation-ID", correlationId);
                    }
                    return execution.execute(request, body);
                });
    }
}
