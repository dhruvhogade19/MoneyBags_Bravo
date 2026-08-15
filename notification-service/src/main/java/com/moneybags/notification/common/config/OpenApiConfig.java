package com.moneybags.notification.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Moneybags Notification Service API",
                version = "Current release",
                description = "Creates customer email notifications and provides notification history.",
                license = @License(name = "Internal Moneybags API")))
public class OpenApiConfig {
}
