package com.moneybags.payments.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI paymentsOpenApi() {
    return new OpenAPI()
        .info(new Info().title("MoneyBags Payments Service API").version("1.0.0")
            .description("Beginner-friendly synchronous payment orchestration service. "
                + "The default demo profile uses in-process peer-service simulators."))
        .components(new Components()
            .addParameters("CorrelationId", new HeaderParameter().name("X-Correlation-Id")
                .description("Optional end-to-end trace identifier")
                .schema(new StringSchema().example("trace-pay-1001"))));
  }
}
