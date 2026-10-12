package com.blackbox.dto;

import com.blackbox.entity.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;

public final class DeliverableDtos {
    private DeliverableDtos() {}
    public record SaveRequest(@NotBlank @Size(max = 255) String title,
            @Size(max = 5000) String description, @NotNull LocalDate dueDate,
            @Size(max = 500) String submissionMethod, UUID ownerId) {}
    public record RequirementRequest(@NotBlank @Size(max = 1000) String content, boolean required) {}
    public record RequirementResponse(UUID id, String content, boolean required) {
        public static RequirementResponse from(DeliverableRequirement r) {
            return new RequirementResponse(r.getId(), r.getContent(), r.isRequired());
        }
    }
    public record Response(UUID id, UUID projectId, String title, String description,
            LocalDate dueDate, String submissionMethod, UUID ownerId, String ownerName,
            List<RequirementResponse> requirements) {
        public static Response from(Deliverable d, List<DeliverableRequirement> requirements) {
            return new Response(d.getId(), d.getProject().getId(), d.getTitle(), d.getDescription(),
                    d.getDueDate(), d.getSubmissionMethod(), d.getOwner() == null ? null : d.getOwner().getId(),
                    d.getOwner() == null ? null : d.getOwner().getName(),
                    requirements.stream().map(RequirementResponse::from).toList());
        }
    }
}
