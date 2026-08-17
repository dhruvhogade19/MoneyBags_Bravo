package com.moneybags.uibff.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {
    @Bean
    @Qualifier("gatewayRestClient")
    RestClient gatewayRestClient(BffProperties properties) {
        return client(properties.gatewayBaseUrl().toString(), properties);
    }

    @Bean
    @Qualifier("identityRestClient")
    RestClient identityRestClient(BffProperties properties) {
        return client(properties.identityBaseUrl().toString(), properties);
    }

    private static RestClient client(String baseUrl, BffProperties properties) {
        // The JDK request factory rejects PATCH, which is required for KYC
        // document decisions and other operational commands proxied by the BFF.
        var requests = new HttpComponentsClientHttpRequestFactory();
        requests.setConnectionRequestTimeout(properties.connectTimeout());
        requests.setReadTimeout(properties.readTimeout());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }
}
