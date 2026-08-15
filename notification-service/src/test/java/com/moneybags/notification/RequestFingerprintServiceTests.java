package com.moneybags.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestFingerprintServiceTests {

    @Test
    void producesTheSameFingerprintRegardlessOfVariableOrder() throws Exception {
        Object service = Class.forName("com.moneybags.notification.notification.service.RequestFingerprintService")
                .getDeclaredConstructor().newInstance();

        assertEquals(fingerprint(service, Map.of("amount", "500.00", "currency", "INR")),
                fingerprint(service, Map.of("currency", "INR", "amount", "500.00")));
    }

    @Test
    void producesDifferentFingerprintsForDifferentRequestContent() throws Exception {
        Object service = Class.forName("com.moneybags.notification.notification.service.RequestFingerprintService")
                .getDeclaredConstructor().newInstance();

        assertNotEquals(fingerprint(service, Map.of("amount", "500.00")),
                fingerprint(service, Map.of("amount", "600.00")));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String fingerprint(Object service, Map<String, String> variables) throws Exception {
        Class<?> requestType = Class.forName("com.moneybags.notification.notification.dto.CreateNotificationRequest");
        Class<? extends Enum> notificationType = (Class<? extends Enum>) Class.forName(
                "com.moneybags.notification.notification.domain.NotificationType");
        Constructor<?> constructor = requestType.getConstructor(Long.class, notificationType, String.class, Map.class);
        Object request = constructor.newInstance(101L, Enum.valueOf(notificationType, "PAYMENT_SUCCESS"), "PAY-10045", variables);
        Method fingerprint = service.getClass().getMethod("fingerprint", requestType);
        return (String) fingerprint.invoke(service, request);
    }
}
