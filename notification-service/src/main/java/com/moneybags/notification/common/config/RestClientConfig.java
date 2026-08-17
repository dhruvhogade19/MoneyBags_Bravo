package com.moneybags.notification.common.config;

import com.moneybags.notification.notification.integration.CifClientProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.http.EurekaClientHttpRequestFactorySupplier;
import org.springframework.cloud.netflix.eureka.http.RestClientDiscoveryClientOptionalArgs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    private final ClientCredentialsTokenProvider tokens;

    public RestClientConfig(ClientCredentialsTokenProvider tokens) {
        this.tokens = tokens;
    }

    @Bean
    RestClientDiscoveryClientOptionalArgs eurekaRestClientOptionalArgs(
            EurekaClientHttpRequestFactorySupplier requestFactorySupplier) {
        return new RestClientDiscoveryClientOptionalArgs(requestFactorySupplier, RestClient::builder);
    }

    @Bean
    @Primary
    RestClient.Builder restClientBuilder() {
        return secured(tokens);
    }

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return secured(tokens);
    }

    @Bean
    RestClient cifRestClient(@LoadBalanced RestClient.Builder builder, CifClientProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }

    private RestClient.Builder secured(ClientCredentialsTokenProvider tokens) {
        return RestClient.builder().requestInterceptor((request, body, execution) -> {
            if (request.getHeaders().getFirst("Authorization") == null) { String token=tokens.token(); if(token!=null)request.getHeaders().setBearerAuth(token); }
            return execution.execute(request,body);
        });
    }
}
