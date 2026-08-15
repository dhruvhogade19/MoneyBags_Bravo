package com.moneybags.notification.notification.dto;

import com.moneybags.notification.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateNotificationRequest(
        @NotNull @Positive Long cifId,
        @NotNull NotificationType notificationType,
        @NotBlank @Size(max = 200) String sourceReference,
        @NotNull @Size(max = 30) Map<
                @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9]*") @Size(max = 50) String,
                @NotBlank @Size(max = 1_000) String> templateVariables) {
}
