package com.blackbox.dto;

import com.blackbox.entity.Alert;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        UUID userId,
        String alertType,
        String severity,
        String message,
        boolean isRead,
        boolean isResolved,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt,
        String resolveReason
) {
    public static AlertResponse from(Alert a) {
        return new AlertResponse(
                a.getId(),
                a.getUser() != null ? a.getUser().getId() : null,
                a.getAlertType(), a.getSeverity(), a.getMessage(),
                a.isRead(), a.isResolved(),
                a.getCreatedAt(), a.getResolvedAt(), a.getResolveReason()
        );
    }
}
