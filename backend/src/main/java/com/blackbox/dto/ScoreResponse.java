package com.blackbox.dto;

import com.blackbox.entity.ContributionScore;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ScoreResponse(
        UUID userId,
        String name,
        String email,
        BigDecimal gitScore,
        BigDecimal docScore,
        BigDecimal meetingScore,
        BigDecimal taskScore,
        BigDecimal totalScore,
        BigDecimal weightGit,
        BigDecimal weightDoc,
        BigDecimal weightMeeting,
        BigDecimal weightTask,
        OffsetDateTime calculatedAt
) {
    public static ScoreResponse from(ContributionScore s) {
        return new ScoreResponse(
                s.getUser().getId(), s.getUser().getName(), s.getUser().getEmail(),
                s.getGitScore(), s.getDocScore(), s.getMeetingScore(), s.getTaskScore(),
                s.getTotalScore(),
                s.getWeightGit(), s.getWeightDoc(), s.getWeightMeeting(), s.getWeightTask(),
                s.getCalculatedAt()
        );
    }
}
