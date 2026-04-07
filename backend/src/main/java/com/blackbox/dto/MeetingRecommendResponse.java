package com.blackbox.dto;

public record MeetingRecommendResponse(
        String suggestedDateTime,  // ISO-8601 (OffsetDateTime.toString())
        String reason
) {}
