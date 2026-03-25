package com.blackbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateTaskStatusRequest(
        @NotBlank @Pattern(regexp = "TODO|IN_PROGRESS|DONE") String status
) {}
