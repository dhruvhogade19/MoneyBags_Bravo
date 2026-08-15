package com.moneybags.notification.notification.service;

import com.moneybags.notification.notification.dto.CreateNotificationRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class RequestFingerprintService {

    public String fingerprint(CreateNotificationRequest request) {
        StringBuilder canonicalRequest = new StringBuilder()
                .append(request.cifId()).append('|')
                .append(request.notificationType().name()).append('|')
                .append(request.sourceReference()).append('|');
        new TreeMap<>(request.templateVariables()).forEach((key, value) -> canonicalRequest
                .append(key.length()).append(':').append(key)
                .append(value.length()).append(':').append(value));
        return sha256(canonicalRequest.toString());
    }

    private String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
