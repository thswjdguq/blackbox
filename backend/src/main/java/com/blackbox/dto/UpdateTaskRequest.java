package com.blackbox.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateTaskRequest(
        @Size(max = 255) String title,
        String description,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
        @Size(max = 30) String tag,
        LocalDate dueDate,
        UUID deliverableId,
        UUID requirementId,
        Boolean clearDeliverable,
        Boolean clearRequirement,
        @Size(max = 2000) String completionCriteria,
        UUID predecessorId,
        Boolean clearPredecessor,
        Boolean attachmentRequired
) {
    public UpdateTaskRequest(String title, String description, String priority, String tag, LocalDate dueDate,
            UUID deliverableId, UUID requirementId, Boolean clearDeliverable, Boolean clearRequirement, String criteria) {
        this(title, description, priority, tag, dueDate, deliverableId, requirementId, clearDeliverable, clearRequirement, criteria, null, null, null);
    }
}
