package com.blackbox.dto;

import com.blackbox.entity.ContributionScore;
import com.blackbox.entity.ContributionScoreItem;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

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
        OffsetDateTime calculatedAt,
        List<ContributionScoreItem> items,
        String criteriaVersion,
        Period period
) {
    public record Period(OffsetDateTime from, OffsetDateTime to) {}
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
                s.getCalculatedAt(),
                s.getItems() == null ? null : List.copyOf(s.getItems()),
                s.getCriteriaVersion(),
                s.getPeriodFrom() == null ? null : new Period(s.getPeriodFrom(), s.getCalculatedAt())
        );
    }
}
