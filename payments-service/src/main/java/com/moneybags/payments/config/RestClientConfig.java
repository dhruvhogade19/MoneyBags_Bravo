package com.moneybags.payments.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@Profile("!test")
public class RestClientConfig {
  private final ClientCredentialsTokenProvider tokens;
  public RestClientConfig(ClientCredentialsTokenProvider tokens) { this.tokens = tokens; }
  @Bean
  @Qualifier("depositRestClient")
  RestClient depositRestClient(@Value("${clients.deposit.base-url}") String baseUrl) {
    return client(baseUrl);
  }

  @Bean
  @Qualifier("creditCardRestClient")
  RestClient creditCardRestClient(@Value("${clients.credit-card.base-url}") String baseUrl) {
    return client(baseUrl);
  }

  @Bean
  @Qualifier("accountingRestClient")
  RestClient accountingRestClient(@Value("${clients.accounting.base-url}") String baseUrl) {
    return client(baseUrl);
  }

  @Bean
  @Qualifier("billingRestClient")
  RestClient billingRestClient(@Value("${clients.billing.base-url}") String baseUrl) {
    return client(baseUrl);
  }

  @Bean
  @Qualifier("notificationRestClient")
  RestClient notificationRestClient(@Value("${clients.notification.base-url}") String baseUrl) {
    return client(baseUrl);
  }

  private RestClient client(String baseUrl) {
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(8));
    return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
        .defaultHeader("Accept", "application/json")
        .requestInterceptor((request, body, execution) -> {
          if (request.getHeaders().getFirst("Authorization") == null) {
            String token=tokens.token(); if(token!=null)request.getHeaders().setBearerAuth(token);
          }
          return execution.execute(request,body);
        }).build();
  }
}
