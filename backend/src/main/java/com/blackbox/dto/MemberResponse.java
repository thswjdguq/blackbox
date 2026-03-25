package com.blackbox.dto;

import com.blackbox.entity.ProjectMember;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MemberResponse(
        UUID memberId,
        UUID userId,
        String name,
        String email,
        String role,
        OffsetDateTime joinedAt,
        boolean consentPlatform,
        boolean consentGithub,
        boolean consentDrive,
        boolean consentAiAnalysis,
        OffsetDateTime consentedAt
) {
    public static MemberResponse from(ProjectMember m) {
        return new MemberResponse(
                m.getId(),
                m.getUser().getId(),
                m.getUser().getName(),
                m.getUser().getEmail(),
                m.getRole(),
                m.getJoinedAt(),
                m.isConsentPlatform(),
                m.isConsentGithub(),
                m.isConsentDrive(),
                m.isConsentAiAnalysis(),
                m.getConsentedAt()
        );
    }
}
