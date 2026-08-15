package com.moneybags.notification.notification.service;

import com.moneybags.notification.common.exception.TemplateRenderingException;
import com.moneybags.notification.notification.entity.NotificationTemplate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9]*)}}" );

    public RenderedEmail render(NotificationTemplate template, Map<String, String> variables) {
        return new RenderedEmail(
                renderPart(template.getEmailSubjectTemplate(), variables),
                renderPart(template.getEmailBodyTemplate(), variables));
    }

    public void validateSourceVariables(NotificationTemplate template, Map<String, String> sourceVariables) {
        Set<String> requiredVariables = placeholdersIn(template.getEmailSubjectTemplate());
        requiredVariables.addAll(placeholdersIn(template.getEmailBodyTemplate()));
        Set<String> unusedVariables = new HashSet<>(sourceVariables.keySet());
        unusedVariables.removeAll(requiredVariables);
        if (!unusedVariables.isEmpty()) {
            throw new TemplateRenderingException("Unused template variables: " + String.join(", ", unusedVariables));
        }
    }

    private Set<String> placeholdersIn(String templatePart) {
        Matcher matcher = PLACEHOLDER.matcher(templatePart);
        Set<String> placeholderNames = new HashSet<>();
        while (matcher.find()) {
            placeholderNames.add(matcher.group(1));
        }
        return placeholderNames;
    }

    private String renderPart(String templatePart, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(templatePart);
        StringBuffer rendered = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String value = variables.get(variableName);

            if (value == null || value.isBlank()) {
                throw new TemplateRenderingException("Missing required template variable: " + variableName);
            }

            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);

        if (PLACEHOLDER.matcher(rendered).find()) {
            throw new TemplateRenderingException("Email template contains an unresolved placeholder");
        }

        return rendered.toString();
    }
}
