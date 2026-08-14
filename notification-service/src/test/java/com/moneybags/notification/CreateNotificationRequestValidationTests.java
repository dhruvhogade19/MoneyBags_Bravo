package com.moneybags.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.Constructor;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreateNotificationRequestValidationTests {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsAValidRequest() throws Exception {
        Object request = request(101L, "PAY-10045", Map.of("amount", "1500.00", "currency", "INR"));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsInvalidIdentifiersAndBlankVariables() throws Exception {
        Object request = request(0L, " ", Map.of("invalid-key", " "));

        assertFalse(validator.validate(request).isEmpty());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object request(Long cifId, String sourceReference, Map<String, String> variables) throws Exception {
        Class<?> requestType = Class.forName("com.moneybags.notification.notification.dto.CreateNotificationRequest");
        Class<? extends Enum> notificationType = (Class<? extends Enum>) Class.forName(
                "com.moneybags.notification.notification.domain.NotificationType");
        Constructor<?> constructor = requestType.getConstructor(Long.class, notificationType, String.class, Map.class);
        return constructor.newInstance(cifId, Enum.valueOf(notificationType, "PAYMENT_SUCCESS"), sourceReference, variables);
    }
}
