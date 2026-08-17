package com.moneybags.creditcard.config;

import org.springframework.cloud.netflix.eureka.http.EurekaClientHttpRequestFactorySupplier;
import org.springframework.cloud.netflix.eureka.http.RestClientDiscoveryClientOptionalArgs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientSecurityConfig {
    @Bean
    RestClientDiscoveryClientOptionalArgs eurekaRestClientOptionalArgs(
            EurekaClientHttpRequestFactorySupplier requestFactorySupplier) {
        return new RestClientDiscoveryClientOptionalArgs(requestFactorySupplier, RestClient::builder);
    }

    @Bean
    RestClient.Builder securedRestClientBuilder(ClientCredentialsTokenProvider tokens) {
        return RestClient.builder().requestInterceptor((request, body, execution) -> {
            if (request.getHeaders().getFirst("Authorization") == null) {
                String token=tokens.token(); if(token!=null)request.getHeaders().setBearerAuth(token);
            }
            return execution.execute(request,body);
        });
    }
}
