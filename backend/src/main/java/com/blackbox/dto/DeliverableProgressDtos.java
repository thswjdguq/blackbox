package com.blackbox.dto;

import java.math.BigDecimal;
import java.util.UUID;

public final class DeliverableProgressDtos {
    private DeliverableProgressDtos() {}

    public record Response(
            UUID deliverableId,
            TaskProgress tasks,
            RequirementProgress requiredRequirements
    ) {}

    public record TaskProgress(long total, long completed, BigDecimal percent) {}

    public record RequirementProgress(
            long total,
            long met,
            BigDecimal percent,
            boolean assessmentAvailable
    ) {}
}
