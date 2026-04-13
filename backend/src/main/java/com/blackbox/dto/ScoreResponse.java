package com.blackbox.dto;

import com.blackbox.entity.ContributionScore;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ScoreResponse(
        UUID    userId,
        String  name,
        String  email,
        boolean taskParticipated,
        boolean meetingParticipated,
        boolean fileParticipated,
        boolean actionParticipated,
        /** FULL | PARTIAL | NONE */
        String  participationLevel,
        OffsetDateTime calculatedAt
) {
    public static ScoreResponse from(ContributionScore s) {
        return new ScoreResponse(
                s.getUser().getId(),
                s.getUser().getName(),
                s.getUser().getEmail(),
                s.isTaskParticipated(),
                s.isMeetingParticipated(),
                s.isFileParticipated(),
                s.isActionParticipated(),
                s.getParticipationLevel(),
                s.getCalculatedAt()
        );
    }
}
