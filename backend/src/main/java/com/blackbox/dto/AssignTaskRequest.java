package com.blackbox.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AssignTaskRequest(
        @NotNull List<UUID> assigneeIds
) {}
