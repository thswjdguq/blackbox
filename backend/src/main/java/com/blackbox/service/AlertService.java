package com.blackbox.service;

import com.blackbox.dto.AlertResponse;
import com.blackbox.entity.*;
import com.blackbox.repository.ActivityLogRepository;
import com.blackbox.repository.AlertRepository;
import com.blackbox.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {

    private final AlertRepository        alertRepository;
    private final ActivityLogRepository  activityLogRepository;
    private final ProjectRepository      projectRepository;

    public AlertService(AlertRepository alertRepository,
                        ActivityLogRepository activityLogRepository,
                        ProjectRepository projectRepository) {
        this.alertRepository        = alertRepository;
        this.activityLogRepository  = activityLogRepository;
        this.projectRepository      = projectRepository;
    }

    // ── 경보 감지 (점수 재계산 후 호출) ───────────────────────────────────

    public void checkAlerts(Project project, List<ProjectMember> members,
                            List<ContributionScore> scores) {
        if (members.size() < 2) return;

        checkFreeRide(project, scores);
        checkOverload(project, scores);
        checkDropout(project, members);
    }

    // ── 경보 조회 ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트 없음: " + projectId));
        return alertRepository.findByProjectOrderByCreatedAtDesc(project).stream()
                .map(AlertResponse::from)
                .toList();
    }

    // ── FREE_RIDE: 미참여(NONE) 감지 ──────────────────────────────────────

    private void checkFreeRide(Project project, List<ContributionScore> scores) {
        for (ContributionScore s : scores) {
            if ("NONE".equals(s.getParticipationLevel())
                    && !alertRepository.existsByProjectAndUserAndAlertTypeAndIsReadFalse(
                    project, s.getUser(), "FREE_RIDE")) {
                createAlert(project, s.getUser(), "FREE_RIDE", "MEDIUM",
                        s.getUser().getName() + "님이 태스크·회의·파일·액션아이템 중 1개 이하에만 참여했습니다 (미참여)");
            }
        }
    }

    // ── OVERLOAD: 혼자 FULL 이고 나머지가 NONE/PARTIAL ────────────────────

    private void checkOverload(Project project, List<ContributionScore> scores) {
        long fullCount    = scores.stream().filter(s -> "FULL".equals(s.getParticipationLevel())).count();
        long nonFullCount = scores.stream().filter(s -> !"FULL".equals(s.getParticipationLevel())).count();

        // FULL 이 1명이고 나머지가 2명 이상 NONE/PARTIAL → 과부하 경보
        if (fullCount == 1 && nonFullCount >= 2) {
            scores.stream()
                    .filter(s -> "FULL".equals(s.getParticipationLevel()))
                    .findFirst()
                    .ifPresent(s -> {
                        if (!alertRepository.existsByProjectAndUserAndAlertTypeAndIsReadFalse(
                                project, s.getUser(), "OVERLOAD")) {
                            createAlert(project, s.getUser(), "OVERLOAD", "MEDIUM",
                                    s.getUser().getName() + "님이 팀에서 유일하게 전체 참여 중입니다 (과부하 위험)");
                        }
                    });
        }
    }

    // ── DROPOUT: 2주 연속 활동 없음 ──────────────────────────────────────

    private void checkDropout(Project project, List<ProjectMember> members) {
        OffsetDateTime twoWeeksAgo = OffsetDateTime.now().minusDays(14);
        for (ProjectMember pm : members) {
            if (pm.getJoinedAt() != null && pm.getJoinedAt().isAfter(twoWeeksAgo)) continue;
            User u = pm.getUser();
            boolean hasActivity = activityLogRepository.hasActivityAfter(project, u, twoWeeksAgo);
            if (!hasActivity
                    && !alertRepository.existsByProjectAndUserAndAlertTypeAndIsReadFalse(project, u, "DROPOUT")) {
                createAlert(project, u, "DROPOUT", "HIGH",
                        u.getName() + "님이 최근 2주 동안 활동 기록이 없습니다");
            }
        }
    }

    // ── 경보 생성 ──────────────────────────────────────────────────────────

    private void createAlert(Project project, User user, String type, String severity, String message) {
        Alert alert = new Alert();
        alert.setProject(project);
        alert.setUser(user);
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alertRepository.save(alert);
    }
}
