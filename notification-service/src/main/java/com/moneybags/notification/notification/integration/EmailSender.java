package com.moneybags.notification.notification.integration;

public interface EmailSender {

    void send(String recipientEmail, String subject, String body);
}
