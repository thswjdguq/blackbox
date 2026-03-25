package com.blackbox.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateMeetingRequest(
        @Size(max = 255) String title,
        @NotNull OffsetDateTime meetingDate,
        String purpose
) {}
