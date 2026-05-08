package com.blackbox.service;

import com.blackbox.dto.AlertResponse;
import com.blackbox.entity.*;
import com.blackbox.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AlertService {

    private final AlertRepository               alertRepository;
    private final ActivityLogRepository         activityLogRepository;
    private final ProjectRepository             projectRepository;
    private final ProjectMemberRepository       memberRepository;
    private final ContributionScoreRepository   scoreRepository;
    private final DiscordNotificationService    discordService;

    public AlertService(AlertRepository alertRepository,
                        ActivityLogRepository activityLogRepository,
                        ProjectRepository projectRepository,
                        ProjectMemberRepository memberRepository,
                        ContributionScoreRepository scoreRepository,
                        DiscordNotificationService discordService) {
        this.alertRepository       = alertRepository;
        this.activityLogRepository = activityLogRepository;
        this.projectRepository     = projectRepository;
        this.memberRepository      = memberRepository;
        this.scoreRepository       = scoreRepository;
        this.discordService        = discordService;
    }

    // ── 경보 조회 (활성만) ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트 없음: " + projectId));
        return alertRepository.findActiveByProject(project).stream()
                .map(AlertResponse::from)
                .toList();
    }

    // ── 경보 감지 + 해제 (점수 재계산 후 호출) ────────────────────────────

    @Transactional
    public void checkAlerts(Project project, List<ProjectMember> members,
                            List<ContributionScore> scores) {
        if (members.size() < 2) return;

        resolveImprovedAlerts(project, scores, members);  // 먼저 해제
        checkFreeRide(project, scores);
        checkOverload(project, scores);
        checkDropout(project, members);
    }

    // ── 이벤트 트리거 재평가 (태스크 완료·체크인·파일 업로드 시 호출) ───────

    @Transactional
    public void reevaluate(User user, Project project) {
        reevaluateFreeRide(user, project);
        reevaluateDropout(user, project);
        reevaluateOverload(project);
    }

    // ── 해제 로직 ─────────────────────────────────────────────────────────

    /** 점수 재계산 결과로 개선된 경보 일괄 해제 */
    private void resolveImprovedAlerts(Project project, List<ContributionScore> scores,
                                       List<ProjectMember> members) {
        for (ContributionScore s : scores) {
            reevaluateFreeRideByScore(s.getUser(), project, s.getParticipationLevel());
            reevaluateOverload(project);
        }
        for (ProjectMember pm : members) {
            reevaluateDropout(pm.getUser(), project);
        }
    }

    /** FREE_RIDE 해제: 참여 여부가 PARTIAL 이상이면 */
    private void reevaluateFreeRide(User user, Project project) {
        scoreRepository.findByProjectAndUser(project, user).ifPresent(score ->
            reevaluateFreeRideByScore(user, project, score.getParticipationLevel())
        );
    }

    private void reevaluateFreeRideByScore(User user, Project project, String level) {
        if ("PARTIAL".equals(level) || "FULL".equals(level)) {
            alertRepository.findByProjectAndUserAndAlertTypeAndResolvedAtIsNull(project, user, "FREE_RIDE")
                    .ifPresent(a -> resolve(a, user.getName() + "님이 참여 활동을 완료했습니다"));
        }
    }

    /** DROPOUT 해제: 최근 7일 내 활동 있으면 */
    private void reevaluateDropout(User user, Project project) {
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        boolean hasActivity = activityLogRepository.hasActivityAfter(project, user, sevenDaysAgo);
        if (hasActivity) {
            alertRepository.findByProjectAndUserAndAlertTypeAndResolvedAtIsNull(project, user, "DROPOUT")
                    .ifPresent(a -> resolve(a, user.getName() + "님이 최근 7일 내 활동했습니다"));
        }
    }

    /** OVERLOAD 해제: FULL 인원이 2명 이상이면 (1인 독점 해소) */
    private void reevaluateOverload(Project project) {
        List<ContributionScore> all = scoreRepository.findByProjectOrderByTotalScoreDesc(project);
        long fullCount = all.stream().filter(s -> "FULL".equals(s.getParticipationLevel())).count();
        if (fullCount >= 2) {
            alertRepository.findActiveByProjectAndType(project, "OVERLOAD")
                    .forEach(a -> resolve(a, "복수 팀원이 전체 참여 중으로 과부하가 해소되었습니다"));
        }
    }

    private void resolve(Alert alert, String reason) {
        alert.setResolvedAt(OffsetDateTime.now());
        alert.setResolveReason(reason);
        alertRepository.save(alert);
    }

    // ── 경보 생성 로직 ────────────────────────────────────────────────────

    private void checkFreeRide(Project project, List<ContributionScore> scores) {
        for (ContributionScore s : scores) {
            if ("NONE".equals(s.getParticipationLevel())
                    && !alertRepository.existsByProjectAndUserAndAlertTypeAndResolvedAtIsNull(
                            project, s.getUser(), "FREE_RIDE")) {
                createAlert(project, s.getUser(), "FREE_RIDE", "MEDIUM",
                        s.getUser().getName() + "님이 태스크·회의·파일·액션아이템 중 1개 이하에만 참여했습니다 (미참여)");
            }
        }
    }

    private void checkOverload(Project project, List<ContributionScore> scores) {
        long fullCount    = scores.stream().filter(s -> "FULL".equals(s.getParticipationLevel())).count();
        long nonFullCount = scores.stream().filter(s -> !"FULL".equals(s.getParticipationLevel())).count();

        if (fullCount == 1 && nonFullCount >= 2) {
            scores.stream()
                    .filter(s -> "FULL".equals(s.getParticipationLevel()))
                    .findFirst()
                    .ifPresent(s -> {
                        if (!alertRepository.existsByProjectAndUserAndAlertTypeAndResolvedAtIsNull(
                                project, s.getUser(), "OVERLOAD")) {
                            createAlert(project, s.getUser(), "OVERLOAD", "MEDIUM",
                                    s.getUser().getName() + "님이 팀에서 유일하게 전체 참여 중입니다 (과부하 위험)");
                        }
                    });
        }
    }

    private void checkDropout(Project project, List<ProjectMember> members) {
        OffsetDateTime twoWeeksAgo = OffsetDateTime.now().minusDays(14);
        for (ProjectMember pm : members) {
            if (pm.getJoinedAt() != null && pm.getJoinedAt().isAfter(twoWeeksAgo)) continue;
            User u = pm.getUser();
            boolean hasActivity = activityLogRepository.hasActivityAfter(project, u, twoWeeksAgo);
            if (!hasActivity
                    && !alertRepository.existsByProjectAndUserAndAlertTypeAndResolvedAtIsNull(project, u, "DROPOUT")) {
                createAlert(project, u, "DROPOUT", "HIGH",
                        u.getName() + "님이 최근 2주 동안 활동 기록이 없습니다");
            }
        }
    }

    private void createAlert(Project project, User user, String type, String severity, String message) {
        Alert alert = new Alert();
        alert.setProject(project);
        alert.setUser(user);
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alertRepository.save(alert);
        discordService.notifyAlert(alert, project);
    }
}
