package com.blackbox.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateMeetingRequest(
        @Size(max = 255) String title,
        @NotNull OffsetDateTime meetingDate,
        String purpose,
        Integer attendanceGraceMinutes,
        Integer attendanceWindowMinutes
) {
    public CreateMeetingRequest(String title, OffsetDateTime date, String purpose) {
        this(title, date, purpose, null, null);
    }
}
