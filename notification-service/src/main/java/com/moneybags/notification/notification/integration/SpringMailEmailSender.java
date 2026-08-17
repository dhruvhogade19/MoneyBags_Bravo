package com.moneybags.notification.notification.integration;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("!mock-mail")
public class SpringMailEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final MailSenderProperties properties;

    public SpringMailEmailSender(JavaMailSender mailSender, MailSenderProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String recipientEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
