package com.moneybags.notification.notification.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moneybags.mail")
public record MailSenderProperties(String fromAddress, String provider) {
}
