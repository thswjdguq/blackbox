package com.blackbox.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActionItemDto(
        String title,
        String assignee,
        @JsonProperty("due_date") String dueDate,
        String priority
) {}
