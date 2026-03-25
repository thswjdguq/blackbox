package com.blackbox.dto;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record UpdateMeetingRequest(
        @Size(max = 255) String title,
        OffsetDateTime meetingDate,
        String purpose,
        String notes,
        String decisions
) {}
