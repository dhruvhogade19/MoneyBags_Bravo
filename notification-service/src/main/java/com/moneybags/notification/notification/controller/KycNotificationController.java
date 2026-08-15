package com.moneybags.notification.notification.controller;

import com.moneybags.notification.notification.dto.KycStatusNotificationRequest;
import com.moneybags.notification.notification.dto.NotificationResponse;
import com.moneybags.notification.notification.service.NotificationService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/notifications/kyc-status")
@Tag(name = "KYC Notifications", description = "KYC status notification adapter")
public class KycNotificationController {

    private final NotificationService notificationService;

    public KycNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @Operation(summary = "Create a notification for a final KYC status")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Notification created"),
            @ApiResponse(responseCode = "200", description = "Idempotent replay"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "422", description = "Rejection reason is missing", content = @Content)
    })
    public ResponseEntity<NotificationResponse> createKycStatusNotification(
            @Valid @RequestBody KycStatusNotificationRequest request) {
        var result = notificationService.createKycStatusNotification(request);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.notification());
    }
}
