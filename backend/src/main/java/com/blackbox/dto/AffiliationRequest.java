package com.blackbox.dto;

import jakarta.validation.constraints.*;

public record AffiliationRequest(
        @NotBlank @Pattern(regexp="CATALOG|SELF_REPORTED") String source,
        @Size(max=64) String schoolId,
        @Size(max=64) String departmentId,
        @Size(max=150) String schoolName,
        @Size(max=150) String departmentName
) {}
