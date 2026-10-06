package com.blackbox.controller;

import com.blackbox.dto.AlertResponse;
import com.blackbox.dto.RiskResponse;
import com.blackbox.dto.ScoreResponse;
import com.blackbox.entity.User;
import com.blackbox.service.AlertService;
import com.blackbox.service.RiskService;
import com.blackbox.service.ScoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ScoreController {

    private final ScoreService scoreService;
    private final AlertService alertService;
    private final RiskService  riskService;

    public ScoreController(ScoreService scoreService, AlertService alertService, RiskService riskService) {
        this.scoreService = scoreService;
        this.alertService = alertService;
        this.riskService  = riskService;
    }

    @GetMapping("/scores")
    public ResponseEntity<List<ScoreResponse>> getScores(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(scoreService.getScores(projectId, user));
    }

    @PostMapping("/scores/recalculate")
    public ResponseEntity<List<ScoreResponse>> recalculate(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(scoreService.recalculate(projectId));
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<AlertResponse>> getAlerts(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(alertService.getAlerts(projectId));
    }

    @PatchMapping("/alerts/{alertId}/read")
    public ResponseEntity<Void> markAlertRead(
            @PathVariable UUID projectId,
            @PathVariable UUID alertId,
            @AuthenticationPrincipal User user) {
        alertService.markAsRead(projectId, alertId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/risk")
    public ResponseEntity<RiskResponse> getRisk(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(riskService.assess(projectId, user));
    }
}
