package com.blackbox.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CalendarRecommendRequest(
        @NotNull UUID projectId,
        @NotNull String targetDate,   // "this_week" | "next_week" | "YYYY-MM-DD"
        List<UUID> attendeeIds,
        String projectDeadline        // YYYY-MM-DD (선택)
) {}
