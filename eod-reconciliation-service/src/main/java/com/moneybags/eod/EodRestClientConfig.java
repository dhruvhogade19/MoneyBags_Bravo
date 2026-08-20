package com.moneybags.eod;

import java.time.Duration;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.http.EurekaClientHttpRequestFactorySupplier;
import org.springframework.cloud.netflix.eureka.http.RestClientDiscoveryClientOptionalArgs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class EodRestClientConfig {
    /** Eureka registry requests must remain direct and must not pass through service discovery. */
    @Bean
    RestClientDiscoveryClientOptionalArgs eodEurekaRestClientOptionalArgs(
            EurekaClientHttpRequestFactorySupplier requestFactorySupplier) {
        return new RestClientDiscoveryClientOptionalArgs(requestFactorySupplier, RestClient::builder);
    }

    @Bean("eodDirectRestClientBuilder")
    RestClient.Builder eodDirectRestClientBuilder() {
        return peerClientBuilder();
    }

    @Bean("eodLoadBalancedRestClientBuilder")
    @LoadBalanced
    RestClient.Builder eodLoadBalancedRestClientBuilder() {
        return peerClientBuilder();
    }

    private RestClient.Builder peerClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }
}
