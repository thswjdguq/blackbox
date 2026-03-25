package com.blackbox.dto;

import com.blackbox.entity.Project;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String courseName,
        String semester,
        LocalDate startDate,
        LocalDate endDate,
        String inviteCode,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long memberCount
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(), p.getName(), p.getDescription(),
                p.getCourseName(), p.getSemester(),
                p.getStartDate(), p.getEndDate(),
                p.getInviteCode(),
                p.getCreatedBy().getId(),
                p.getCreatedAt(), p.getUpdatedAt(),
                0L
        );
    }

    public static ProjectResponse from(Project p, long memberCount) {
        return new ProjectResponse(
                p.getId(), p.getName(), p.getDescription(),
                p.getCourseName(), p.getSemester(),
                p.getStartDate(), p.getEndDate(),
                p.getInviteCode(),
                p.getCreatedBy().getId(),
                p.getCreatedAt(), p.getUpdatedAt(),
                memberCount
        );
    }
}
