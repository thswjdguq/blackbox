package com.blackbox.dto;

import com.blackbox.entity.Meeting;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeetingResponse(
        UUID id,
        UUID projectId,
        String title,
        OffsetDateTime meetingDate,
        String purpose,
        String notes,
        String decisions,
        String checkinCode,
        String aiSummary,
        String notionPageId,
        OffsetDateTime notionSyncedAt,
        UUID createdBy,
        OffsetDateTime createdAt
) {
    public static MeetingResponse from(Meeting m) {
        return new MeetingResponse(
                m.getId(), m.getProject().getId(),
                m.getTitle(), m.getMeetingDate(),
                m.getPurpose(), m.getNotes(), m.getDecisions(),
                m.getCheckinCode(),
                m.getAiSummary(), m.getNotionPageId(), m.getNotionSyncedAt(),
                m.getCreatedBy().getId(),
                m.getCreatedAt()
        );
    }
}
