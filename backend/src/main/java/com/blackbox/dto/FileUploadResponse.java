package com.blackbox.dto;

import com.blackbox.entity.FileVault;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FileUploadResponse(
        UUID id,
        UUID projectId,
        String fileName,
        String fileHash,
        Long fileSize,
        Integer version,
        boolean tamperDetected,
        OffsetDateTime uploadedAt,
        String notionPageUrl        // null if Notion not configured or sync failed
) {
    public static FileUploadResponse of(FileVault v, boolean tamperDetected) {
        return new FileUploadResponse(
                v.getId(), v.getProject().getId(),
                v.getFileName(), v.getFileHash(), v.getFileSize(),
                v.getVersion(), tamperDetected, v.getUploadedAt(), null
        );
    }

    public static FileUploadResponse of(FileVault v, boolean tamperDetected, String notionPageUrl) {
        return new FileUploadResponse(
                v.getId(), v.getProject().getId(),
                v.getFileName(), v.getFileHash(), v.getFileSize(),
                v.getVersion(), tamperDetected, v.getUploadedAt(), notionPageUrl
        );
    }
}
