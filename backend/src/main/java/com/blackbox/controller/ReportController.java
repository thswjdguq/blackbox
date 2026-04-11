package com.blackbox.controller;

import com.blackbox.entity.User;
import com.blackbox.service.EvidencePackageService;
import com.blackbox.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ReportController {

    private final ReportService           reportService;
    private final EvidencePackageService  evidencePackageService;

    public ReportController(ReportService reportService,
                            EvidencePackageService evidencePackageService) {
        this.reportService          = reportService;
        this.evidencePackageService = evidencePackageService;
    }

    @GetMapping("/report")
    public ResponseEntity<byte[]> downloadReport(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {

        byte[] pdf = reportService.generateReport(projectId, user);

        String filename = "blackbox-report-" + LocalDate.now() + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    @GetMapping("/evidence-package")
    public ResponseEntity<byte[]> downloadEvidencePackage(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {

        byte[] zip = evidencePackageService.generatePackage(projectId, user);

        String filename = "blackbox-evidence-" + LocalDate.now() + ".zip";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(zip.length)
                .body(zip);
    }
}
