package com.blackbox.dto;

import com.blackbox.entity.Task;
import com.blackbox.entity.TaskAssignee;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID projectId,
        String title,
        String description,
        String status,
        String priority,
        String tag,
        LocalDate dueDate,
        OffsetDateTime completedAt,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<AssigneeSummary> assignees,
        UUID deliverableId,
        String deliverableTitle,
        UUID requirementId,
        String requirementContent,
        String completionCriteria
) {
    public record AssigneeSummary(UUID userId, String name, String email) {}

    public static TaskResponse from(Task t, List<TaskAssignee> assignees) {
        List<AssigneeSummary> summaries = assignees.stream()
                .map(a -> new AssigneeSummary(a.getUser().getId(), a.getUser().getName(), a.getUser().getEmail()))
                .toList();
        return new TaskResponse(
                t.getId(), t.getProject().getId(),
                t.getTitle(), t.getDescription(),
                t.getStatus(), t.getPriority(), t.getTag(),
                t.getDueDate(), t.getCompletedAt(),
                t.getCreatedBy().getId(),
                t.getCreatedAt(), t.getUpdatedAt(),
                summaries,
                t.getDeliverable() == null ? null : t.getDeliverable().getId(),
                t.getDeliverable() == null ? null : t.getDeliverable().getTitle(),
                t.getRequirement() == null ? null : t.getRequirement().getId(),
                t.getRequirement() == null ? null : t.getRequirement().getContent(),
                t.getCompletionCriteria()
        );
    }
}
