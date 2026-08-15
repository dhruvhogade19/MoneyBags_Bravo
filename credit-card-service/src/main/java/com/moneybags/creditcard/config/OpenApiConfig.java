package com.moneybags.creditcard.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI creditCardOpenApi() {
        return new OpenAPI().info(new Info().title("Moneybags Credit Card Service").version("v1")
                        .description("Credit-card application, account, credit-reservation, repayment, and EOD APIs. "
                                + "Authentication and authorisation are not currently implemented."))
                .addTagsItem(new Tag().name("Customer / Admin - Applications")
                        .description("Credit-card application submission and decisions."))
                .addTagsItem(new Tag().name("Customer / Admin - Accounts")
                        .description("Credit-card account views and lifecycle operations."))
                .addTagsItem(new Tag().name("Internal - Payment Service")
                        .description("Authoritative HOLD → CAPTURE / RELEASE and bill-payment operations."))
                .addTagsItem(new Tag().name("Internal - EOD Operations")
                        .description("Operational end-of-day readiness check."));
    }
}
