package com.blackbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
        @Size(max = 30) String tag,
        LocalDate dueDate,
        List<UUID> assigneeIds,
        @Pattern(regexp = "TODO|IN_PROGRESS|DONE") String status,
        UUID deliverableId,
        UUID requirementId,
        @Size(max = 2000) String completionCriteria,
        UUID predecessorId,
        Boolean attachmentRequired
) {
    public CreateTaskRequest(String title, String description, String priority, String tag, LocalDate dueDate,
            List<UUID> assigneeIds, String status, UUID deliverableId, UUID requirementId, String criteria) {
        this(title, description, priority, tag, dueDate, assigneeIds, status, deliverableId, requirementId, criteria, null, null);
    }
}
