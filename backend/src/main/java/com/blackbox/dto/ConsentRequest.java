package com.blackbox.dto;

public record ConsentRequest(
        boolean consentPlatform,
        boolean consentGithub,
        boolean consentDrive,
        boolean consentAiAnalysis
) {}
