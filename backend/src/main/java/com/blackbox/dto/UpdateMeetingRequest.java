package com.blackbox.dto;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record UpdateMeetingRequest(
        @Size(max = 255) String title,
        OffsetDateTime meetingDate,
        String purpose,
        String notes,
        String decisions,
        Integer attendanceGraceMinutes,
        Integer attendanceWindowMinutes
) {
    public UpdateMeetingRequest(String title, OffsetDateTime date, String purpose, String notes, String decisions) {
        this(title, date, purpose, notes, decisions, null, null);
    }
}
