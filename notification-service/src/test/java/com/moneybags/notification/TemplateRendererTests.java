package com.moneybags.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTests {

    @Test
    void replacesVariablesInSubjectAndBody() throws Exception {
        Object template = template("Payment of {{amount}}", "Hello {{firstName}}, paid {{amount}}.");

        Object email = render(template, Map.of("firstName", "Charuvi", "amount", "500.00"));

        assertThat(email.getClass().getMethod("subject").invoke(email)).isEqualTo("Payment of 500.00");
        assertThat(email.getClass().getMethod("body").invoke(email)).isEqualTo("Hello Charuvi, paid 500.00.");
    }

    @Test
    void rejectsMissingTemplateVariable() throws Exception {
        Object template = template("Payment", "Hello {{firstName}}, paid {{amount}}.");

        assertThatThrownBy(() -> render(template, Map.of("firstName", "Charuvi")))
                .hasRootCauseMessage("Missing required template variable: amount");
    }

    @Test
    void rejectsUnusedTemplateVariable() throws Exception {
        Object template = template("Payment", "Hello {{firstName}}.");

        assertThatThrownBy(() -> validateSourceVariables(template, Map.of("firstName", "Charuvi", "amount", "500.00")))
                .hasRootCauseMessage("Unused template variables: amount");
    }

    private Object template(String subject, String body) throws Exception {
        Class<?> templateType = Class.forName("com.moneybags.notification.notification.entity.NotificationTemplate");
        var constructor = templateType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object template = constructor.newInstance();
        set(template, "emailSubjectTemplate", subject);
        set(template, "emailBodyTemplate", body);
        return template;
    }

    private Object render(Object template, Map<String, String> variables) throws Exception {
        Class<?> rendererType = Class.forName("com.moneybags.notification.notification.service.TemplateRenderer");
        Object renderer = rendererType.getDeclaredConstructor().newInstance();
        Method render = rendererType.getMethod("render", template.getClass(), Map.class);
        try {
            return render.invoke(renderer, template, variables);
        } catch (InvocationTargetException exception) {
            throw exception;
        }
    }

    private void validateSourceVariables(Object template, Map<String, String> variables) throws Exception {
        Class<?> rendererType = Class.forName("com.moneybags.notification.notification.service.TemplateRenderer");
        Object renderer = rendererType.getDeclaredConstructor().newInstance();
        Method validate = rendererType.getMethod("validateSourceVariables", template.getClass(), Map.class);
        validate.invoke(renderer, template, variables);
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
