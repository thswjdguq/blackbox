package com.blackbox.dto;

public record NotionSyncResponse(
        String  pageUrl,
        int     taskCount,
        String  message
) {}
