package com.blackbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateMemberRoleRequest(
        @NotBlank @Pattern(regexp = "LEADER|MEMBER|OBSERVER") String role
) {}
