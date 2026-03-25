package com.blackbox.dto;

import com.blackbox.entity.FileVault;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FileHistoryResponse(
        UUID id,
        String fileName,
        String fileHash,
        Long fileSize,
        Integer version,
        UUID uploaderId,
        String uploaderName,
        OffsetDateTime uploadedAt
) {
    public static FileHistoryResponse from(FileVault v) {
        return new FileHistoryResponse(
                v.getId(), v.getFileName(), v.getFileHash(), v.getFileSize(),
                v.getVersion(),
                v.getUploader().getId(), v.getUploader().getName(),
                v.getUploadedAt()
        );
    }
}
