package com.blackbox.service;

import com.blackbox.dto.AlertResponse;
import com.blackbox.entity.*;
import com.blackbox.repository.ActivityLogRepository;
import com.blackbox.repository.AlertRepository;
import com.blackbox.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {

    private static final BigDecimal FREE_RIDE_THRESHOLD = new BigDecimal("0.60"); // 팀 평균의 60% 미만
    private static final double OVERLOAD_THRESHOLD = 0.60;                       // 총합의 60% 초과

    private final AlertRepository alertRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ProjectRepository projectRepository;

    public AlertService(AlertRepository alertRepository,
                        ActivityLogRepository activityLogRepository,
                        ProjectRepository projectRepository) {
        this.alertRepository = alertRepository;
        this.activityLogRepository = activityLogRepository;
        this.projectRepository = projectRepository;
    }

    // ── 경보 감지 (점수 재계산 후 호출) ───────────────────────────────────

    public void checkAlerts(Project project, List<ProjectMember> members,
                            List<ContributionScore> scores) {
        if (members.size() < 2) return; // 1인 프로젝트는 감지 불필요

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

    // ── FREE_RIDE: 팀 평균 60% 미만 ───────────────────────────────────────

    private void checkFreeRide(Project project, List<ContributionScore> scores) {
        if (scores.isEmpty()) return;
        BigDecimal avg = scores.stream()
                .map(ContributionScore::getTotalScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 2, java.math.RoundingMode.HALF_UP);

        BigDecimal threshold = avg.multiply(FREE_RIDE_THRESHOLD);

        for (ContributionScore s : scores) {
            if (s.getTotalScore().compareTo(threshold) < 0
                    && !alertRepository.existsByProjectAndUserAndAlertTypeAndIsReadFalse(
                    project, s.getUser(), "FREE_RIDE")) {
                createAlert(project, s.getUser(), "FREE_RIDE", "MEDIUM",
                        s.getUser().getName() + "님의 기여도 점수(" + s.getTotalScore()
                                + ")가 팀 평균(" + avg + ")의 60% 미만입니다");
            }
        }
    }

    // ── OVERLOAD: 1인이 총합의 60% 초과 ──────────────────────────────────

    private void checkOverload(Project project, List<ContributionScore> scores) {
        BigDecimal total = scores.stream()
                .map(ContributionScore::getTotalScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) <= 0) return;

        for (ContributionScore s : scores) {
            double ratio = s.getTotalScore().doubleValue() / total.doubleValue();
            if (ratio > OVERLOAD_THRESHOLD
                    && !alertRepository.existsByProjectAndUserAndAlertTypeAndIsReadFalse(
                    project, s.getUser(), "OVERLOAD")) {
                createAlert(project, s.getUser(), "OVERLOAD", "MEDIUM",
                        s.getUser().getName() + "님이 팀 전체 기여의 "
                                + String.format("%.0f%%", ratio * 100) + "를 담당하고 있습니다 (과부하 위험)");
            }
        }
    }

    // ── DROPOUT: 2주 연속 활동 없음 ──────────────────────────────────────

    private void checkDropout(Project project, List<ProjectMember> members) {
        OffsetDateTime twoWeeksAgo = OffsetDateTime.now().minusDays(14);
        for (ProjectMember pm : members) {
            // 가입 후 14일이 지난 멤버만 검사
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
