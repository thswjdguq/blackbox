package com.blackbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CalendarEventRequest(
        @NotBlank String title,
        @NotNull String startTime,   // ISO-8601
        @NotNull String endTime,     // ISO-8601
        List<UUID> attendeeIds,
        String description
) {}
