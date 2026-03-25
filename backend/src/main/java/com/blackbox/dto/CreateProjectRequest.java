package com.blackbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateProjectRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @Size(max = 255) String courseName,
        @Size(max = 20) String semester,
        LocalDate startDate,
        LocalDate endDate
) {}
