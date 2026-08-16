package com.moneybags.kycservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.http.EurekaClientHttpRequestFactorySupplier;
import org.springframework.cloud.netflix.eureka.http.RestClientDiscoveryClientOptionalArgs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClientDiscoveryClientOptionalArgs eurekaRestClientOptionalArgs(
            EurekaClientHttpRequestFactorySupplier requestFactorySupplier) {
        return new RestClientDiscoveryClientOptionalArgs(requestFactorySupplier, RestClient::builder);
    }

    @Bean
    @Primary
    public RestClient.Builder restClientBuilder(ClientCredentialsTokenProvider tokens) {
        return secured(tokens);
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder(ClientCredentialsTokenProvider tokens) {
        return secured(tokens);
    }

    private RestClient.Builder secured(ClientCredentialsTokenProvider tokens) {
        return RestClient.builder().requestInterceptor((request, body, execution) -> {
            if (request.getHeaders().getFirst("Authorization") == null) { String token=tokens.token(); if(token!=null)request.getHeaders().setBearerAuth(token); }
            return execution.execute(request,body);
        });
    }
}
