package com.moneybags.notification.notification.controller;

import com.moneybags.notification.notification.dto.NotificationResponse;
import com.moneybags.notification.notification.service.NotificationHistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Validated
@Tag(name = "Notification History", description = "Customer notification history")
public class NotificationHistoryController {

    private final NotificationHistoryService notificationHistoryService;

    public NotificationHistoryController(NotificationHistoryService notificationHistoryService) {
        this.notificationHistoryService = notificationHistoryService;
    }

    @GetMapping
    @Operation(summary = "Get notification history", description = "Returns a customer's notifications, newest first.")
    @ApiResponse(responseCode = "200", description = "Notification history returned")
    public Page<NotificationResponse> getHistory(
            @RequestParam @NotNull @Positive Long cifId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notificationHistoryService.getHistory(cifId, page, size);
    }

    @GetMapping("/{notificationId}")
    @Operation(summary = "Get a notification by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification returned"),
            @ApiResponse(responseCode = "404", description = "Notification not found", content = @Content)
    })
    public NotificationResponse getNotification(@PathVariable @Positive Long notificationId) {
        return notificationHistoryService.getById(notificationId);
    }
}
