package com.moneybags.deposit.service;

import com.moneybags.deposit.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PiiProtector {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public PiiProtector(@Value("${moneybags.security.pii-key-base64:}") String base64Key) {
        this.key = base64Key == null || base64Key.isBlank() ? null : Base64.getDecoder().decode(base64Key);
        if (key != null && key.length != 32) throw new IllegalArgumentException("PII key must be 32 bytes (Base64 encoded)");
    }

    public String encrypt(String plainText) {
        if (key == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PII_ENCRYPTION_NOT_CONFIGURED",
                "Nominee data requires MONEYBAGS_PII_KEY_BASE64");
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt PII", ex);
        }
    }
}

