package com.blackbox.controller;

import com.blackbox.dto.FileHistoryResponse;
import com.blackbox.dto.FileUploadResponse;
import com.blackbox.entity.User;
import com.blackbox.service.FileVaultService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
public class FileVaultController {

    private final FileVaultService fileVaultService;

    public FileVaultController(FileVaultService fileVaultService) {
        this.fileVaultService = fileVaultService;
    }

    // ── 업로드 ─────────────────────────────────────────────────────────────

    @PostMapping("/api/projects/{projectId}/files")
    public ResponseEntity<FileUploadResponse> upload(
            @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {
        return ResponseEntity.ok(fileVaultService.upload(projectId, file, user));
    }

    // ── 파일 목록 ──────────────────────────────────────────────────────────

    @GetMapping("/api/projects/{projectId}/files")
    public ResponseEntity<List<FileHistoryResponse>> listFiles(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(fileVaultService.listFiles(projectId, user));
    }

    // ── 버전 이력 ──────────────────────────────────────────────────────────

    @GetMapping("/api/projects/{projectId}/files/history")
    public ResponseEntity<List<FileHistoryResponse>> fileHistory(
            @PathVariable UUID projectId,
            @RequestParam String fileName,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(fileVaultService.fileHistory(projectId, fileName, user));
    }

    // ── 다운로드 ───────────────────────────────────────────────────────────

    @GetMapping("/api/files/{fileId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal User user) {
        FileVaultService.DownloadResult result = fileVaultService.download(fileId, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(result.fileName()))
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(result.fileSize()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(result.resource());
    }

    /**
     * 이름을 그대로 이어 붙이면 한글 이름을 브라우저가 읽지 못하고, 따옴표가 든 이름은 머리를 깨뜨린다.
     * 영문 이름은 예전처럼 filename만 보내고, 그 밖의 이름은 filename*(RFC 5987)를 함께 보낸다.
     */
    static String attachment(String fileName) {
        var disposition = ContentDisposition.attachment();
        boolean ascii = StandardCharsets.US_ASCII.newEncoder().canEncode(fileName);
        return (ascii ? disposition.filename(fileName) : disposition.filename(fileName, StandardCharsets.UTF_8)).build().toString();
    }
}
