package com.blackbox.service;

import com.blackbox.dto.FileHistoryResponse;
import com.blackbox.dto.FileUploadResponse;
import com.blackbox.entity.*;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FileVaultService {
    private static final Logger log = LoggerFactory.getLogger(FileVaultService.class);

    private final FileVaultRepository fileVaultRepository;
    private final TamperDetectionLogRepository tamperLogRepository;
    private final HashService hashService;
    private final FileStorageService fileStorageService;
    private final ProjectAccessChecker accessChecker;
    private final ActivityLogService activityLogService;
    private final AlertService alertService;
    private final NotionService notionService;

    public FileVaultService(FileVaultRepository fileVaultRepository,
                            TamperDetectionLogRepository tamperLogRepository,
                            HashService hashService,
                            FileStorageService fileStorageService,
                            ProjectAccessChecker accessChecker,
                            ActivityLogService activityLogService,
                            AlertService alertService,
                            NotionService notionService) {
        this.fileVaultRepository = fileVaultRepository;
        this.tamperLogRepository = tamperLogRepository;
        this.hashService = hashService;
        this.fileStorageService = fileStorageService;
        this.accessChecker = accessChecker;
        this.activityLogService = activityLogService;
        this.alertService = alertService;
        this.notionService = notionService;
    }

    // ── 업로드 ─────────────────────────────────────────────────────────────

    @Transactional
    public FileUploadResponse upload(UUID projectId, MultipartFile file, User uploader) throws IOException {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireContributor(project, uploader);

        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unknown";
        String newHash = hashService.sha256(file);
        long fileSize = file.getSize();

        // 기존 버전 조회
        FileVault prev = fileVaultRepository
                .findTopByProjectAndFileNameOrderByVersionDesc(project, originalName)
                .orElse(null);

        boolean tamperDetected = false;
        int nextVersion = 1;

        if (prev != null) {
            nextVersion = prev.getVersion() + 1;
            if (!prev.getFileHash().equals(newHash)) {
                // 같은 이름으로 내용이 다른 파일은 수정본이다. 변경 기록만 남기고 경보는 만들지 않는다(K-10 5장)
                tamperDetected = true;
                recordTamper(prev, newHash);
            }
            // 동일 해시면 중복 업로드 — 버전만 증가
        }

        // 파일 디스크 저장
        String storagePath = fileStorageService.store(file, projectId);
        deleteIfRolledBack(storagePath);

        // file_vault INSERT (immutable)
        FileVault vault = new FileVault();
        vault.setProject(project);
        vault.setUploader(uploader);
        vault.setFileName(originalName);
        vault.setFileHash(newHash);
        vault.setFileSize(fileSize);
        vault.setStoragePath(storagePath);
        vault.setVersion(nextVersion);
        vault.setImmutable(true);
        vault.setUploadedAt(OffsetDateTime.now()); // @Immutable 엔티티는 refresh 불가 → Java에서 직접 설정
        fileVaultRepository.save(vault);

        // activity_log 기록 (신규=FILE_CREATE, 재업로드=FILE_EDIT)
        String actionType = nextVersion == 1 ? "FILE_CREATE" : "FILE_EDIT";
        activityLogService.record(project, uploader, actionType,
                "{\"fileId\":\"" + vault.getId() + "\",\"fileName\":\"" + escapeJson(originalName)
                        + "\",\"version\":" + nextVersion + "}");

        // Notion 자동 동기화 (실패해도 업로드 자체는 성공)
        String notionPageUrl = null;
        if (notionService.isConfigured()) {
            try {
                notionPageUrl = notionService.syncFileEntry(
                        project.getName(),
                        originalName,
                        newHash,
                        uploader.getName(),
                        nextVersion,
                        fileSize,
                        vault.getUploadedAt()
                );
            } catch (Exception ignored) {
                // Notion 동기화 실패는 업로드 결과에 영향 없음
            }
        }

        alertService.reevaluate(uploader, project);

        return FileUploadResponse.of(vault, tamperDetected, notionPageUrl);
    }

    // ── 다운로드 ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DownloadResult download(UUID fileId, User user) {
        FileVault vault = fileVaultRepository.findById(fileId)
                .orElseThrow(() -> new NotFoundException("파일을 찾을 수 없습니다"));
        accessChecker.requireMember(vault.getProject(), user);
        Resource resource = fileStorageService.load(vault.getStoragePath());
        return new DownloadResult(vault.getFileName(), vault.getFileSize(), resource);
    }

    public record DownloadResult(String fileName, Long fileSize, Resource resource) {}

    // ── 파일 목록 ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FileHistoryResponse> listFiles(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        return fileVaultRepository.findByProjectOrderByUploadedAtDesc(project).stream()
                .map(FileHistoryResponse::from)
                .toList();
    }

    // ── 파일 버전 이력 ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FileHistoryResponse> fileHistory(UUID projectId, String fileName, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        return fileVaultRepository
                .findByProjectAndFileNameOrderByVersionDesc(project, fileName).stream()
                .map(FileHistoryResponse::from)
                .toList();
    }

    // ── internal ──────────────────────────────────────────────────────────

    private void recordTamper(FileVault originalVault, String newHash) {
        TamperDetectionLog log = new TamperDetectionLog();
        log.setVault(originalVault);
        log.setOriginalHash(originalVault.getFileHash());
        log.setNewHash(newHash);
        log.setDetectorType("REUPLOAD");
        log.setStatus("FLAGGED");
        tamperLogRepository.save(log);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * DB 기록이 되돌아가면 디스크에 쓴 파일도 지운다. 같은 트랜잭션에서 업로드 다음 일(검토 회차 열기 등)이 실패해도 주인 없는 파일이 남지 않는다.
     * 커밋 여부를 알 수 없을 때(STATUS_UNKNOWN)는 기록이 남았을 수 있어 지우지 않는다
     */
    private static void deleteIfRolledBack(String storagePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) return;
                try {
                    Files.deleteIfExists(Path.of(storagePath));
                } catch (IOException e) {
                    log.warn("되돌린 업로드의 파일을 지우지 못했습니다 path={}", storagePath, e);
                }
            }
        });
    }

}
