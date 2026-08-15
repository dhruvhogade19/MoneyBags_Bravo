package com.moneybags.cif.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp,
        Map<String, String> validationErrors
) {
}

//This DTO defines one consistent format for all API errors.
//For example, if an email is already registered, the API can return:
//{
//  "status": 409,
//  "error": "Conflict",
//  "message": "Email is already registered",
//  "timestamp": "2026-08-12T10:30:00",
//  "validationErrors": null
//}