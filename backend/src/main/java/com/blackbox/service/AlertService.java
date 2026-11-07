package com.blackbox.service;

import com.blackbox.dto.AlertResponse;
import com.blackbox.entity.*;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AlertService {
    // ReportService(PDF)도 화면과 같은 대상·문구를 쓰도록 같은 패키지에 공개한다.
    static final Set<String> PARTICIPATION_TYPES=Set.of("FREE_RIDE","DROPOUT","OVERLOAD");
    static final String PARTICIPATION_NOTICE="자동 집계된 참여 기록이 적습니다. 검토·답변 등 집계에 반영되지 않은 활동이 있을 수 있으니 팀과 함께 확인해 주세요.";

    private final AlertRepository               alertRepository;
    private final ActivityLogRepository         activityLogRepository;
    private final ProjectRepository             projectRepository;
    private final ProjectMemberRepository       memberRepository;
    private final ContributionScoreRepository   scoreRepository;
    private final DiscordNotificationService    discordService;
    private final ProjectAccessChecker         accessChecker;

    public AlertService(AlertRepository alertRepository,
                        ActivityLogRepository activityLogRepository,
                        ProjectRepository projectRepository,
                        ProjectMemberRepository memberRepository,
                        ContributionScoreRepository scoreRepository,
                        DiscordNotificationService discordService,
                        ProjectAccessChecker accessChecker) {
        this.alertRepository       = alertRepository;
        this.activityLogRepository = activityLogRepository;
        this.projectRepository     = projectRepository;
        this.memberRepository      = memberRepository;
        this.scoreRepository       = scoreRepository;
        this.discordService        = discordService;
        this.accessChecker         = accessChecker;
    }

    // ── 읽음 처리 ─────────────────────────────────────────────────────────

    @Transactional
    public void markAsRead(UUID projectId, UUID alertId, User user) {
        Project project = accessChecker.getProject(projectId);
        // 읽음 상태는 팀 공용 데이터의 변경이므로 관찰자의 읽기 전용 권한으로 바꿀 수 없다.
        accessChecker.requireContributor(project, user);
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new NotFoundException("경보를 찾을 수 없습니다"));
        if (!alert.getProject().getId().equals(projectId)) {
            // 다른 프로젝트 경보의 존재 여부도 공개하지 않는다.
            throw new NotFoundException("경보를 찾을 수 없습니다");
        }
        alert.setRead(true);
        alertRepository.save(alert);
    }

    // ── 경보 조회 (활성만) ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        Set<UUID> contributors=contributorIds(project);
        return alertRepository.findActiveByProject(project).stream()
                .filter(a->!PARTICIPATION_TYPES.contains(a.getAlertType())
                        || (a.getUser()!=null && contributors.contains(a.getUser().getId())))
                .map(a->{
                    AlertResponse response=AlertResponse.from(a);
                    if (!"FREE_RIDE".equals(a.getAlertType())) return response;
                    // 이전 경보도 읽기 응답에서만 안내 문구로 바꾼다. 원본 기록은 보존한다.
                    return new AlertResponse(response.id(),response.userId(),response.alertType(),response.severity(),
                            PARTICIPATION_NOTICE,response.isRead(),response.isResolved(),response.createdAt(),
                            response.resolvedAt(),response.resolveReason());
                })
                .toList();
    }

    // ── 경보 감지 + 해제 (점수 재계산 후 호출) ────────────────────────────

    @Transactional
    public void checkAlerts(Project project, List<ProjectMember> members,
                            List<ContributionScore> scores) {
        members=currentContributors(project);
        if (members.size() < 2) return;
        Set<UUID> ids=members.stream().map(m->m.getUser().getId()).collect(Collectors.toSet());
        scores=scores.stream().filter(s->ids.contains(s.getUser().getId())).toList();

        resolveImprovedAlerts(project, scores, members);  // 먼저 해제
        checkFreeRide(project, scores);
        checkOverload(project, scores);
        checkDropout(project, members);
    }

    // ── 이벤트 트리거 재평가 (태스크 완료·체크인·파일 업로드 시 호출) ───────

    @Transactional
    public void reevaluate(User user, Project project) {
        if (!contributorIds(project).contains(user.getId())) return;
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
        Set<UUID> ids=contributorIds(project);
        List<ContributionScore> all = scoreRepository.findByProjectOrderByTotalScoreDesc(project);
        long fullCount = all.stream().filter(s->ids.contains(s.getUser().getId()))
                .filter(s -> "FULL".equals(s.getParticipationLevel())).count();
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
                        PARTICIPATION_NOTICE);
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
    private List<ProjectMember> currentContributors(Project project) {
        return memberRepository.findByProject(project).stream().filter(ProjectAccessChecker::canContribute).toList();
    }
    private Set<UUID> contributorIds(Project project) {
        return currentContributors(project).stream().map(m->m.getUser().getId()).collect(Collectors.toSet());
    }
}
