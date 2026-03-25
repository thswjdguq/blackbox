package com.blackbox.dto;

import com.blackbox.entity.MeetingAttendee;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendeeResponse(
        UUID userId,
        String name,
        String email,
        boolean checkedIn,
        OffsetDateTime checkedAt
) {
    public static AttendeeResponse from(MeetingAttendee a) {
        return new AttendeeResponse(
                a.getUser().getId(),
                a.getUser().getName(),
                a.getUser().getEmail(),
                a.isCheckedIn(),
                a.getCheckedAt()
        );
    }
}
