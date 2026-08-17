package com.moneybags.uibff.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moneybags.bff")
public record BffProperties(URI gatewayBaseUrl, URI identityBaseUrl, String uiStaticLocation,
                            Duration connectTimeout, Duration readTimeout) {
}
