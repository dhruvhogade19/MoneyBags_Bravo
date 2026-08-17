package com.moneybags.notification.notification.integration;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mock-mail")
public class MockEmailSender implements EmailSender {

    @Override
    public void send(String recipientEmail, String subject, String body) {
        // Intentionally succeeds: used only for local development without an SMTP server.
    }
}
