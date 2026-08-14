package com.moneybags.notification.notification.controller;

import com.moneybags.notification.notification.dto.CreateNotificationRequest;
import com.moneybags.notification.notification.dto.NotificationResponse;
import com.moneybags.notification.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/notifications")
@Validated
@Tag(name = "Notifications", description = "Generic notification creation for internal source services")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @Operation(summary = "Create a notification", description = "Creates and sends a notification, or replays the original result for the same idempotency key.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Notification created"),
            @ApiResponse(responseCode = "200", description = "Idempotent replay"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "409", description = "Idempotency conflict", content = @Content),
            @ApiResponse(responseCode = "422", description = "Customer or template data cannot be used", content = @Content),
            @ApiResponse(responseCode = "503", description = "CIF service unavailable", content = @Content)
    })
    public ResponseEntity<NotificationResponse> createNotification(
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
                    description = "Stable unique key used to prevent duplicate notifications")
            @RequestHeader("Idempotency-Key")
            @NotBlank @Size(max = 150) @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            @Valid @RequestBody CreateNotificationRequest request) {
        var result = notificationService.createOrReplay(request, idempotencyKey);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.notification());
    }
}
