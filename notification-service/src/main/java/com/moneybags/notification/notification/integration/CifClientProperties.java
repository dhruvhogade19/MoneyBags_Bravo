package com.moneybags.notification.notification.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moneybags.cif")
public record CifClientProperties(String baseUrl) {
}
