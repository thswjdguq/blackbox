package com.blackbox.service;

import com.blackbox.dto.ScoreResponse;
import com.blackbox.entity.*;
import com.blackbox.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ScoreService {

    private static final Logger log = LoggerFactory.getLogger(ScoreService.class);

    private static final List<String> GIT_ACTIONS     = List.of("COMMIT", "PR_CREATE", "PR_REVIEW");
    private static final List<String> DOC_ACTIONS     = List.of("FILE_CREATE", "FILE_EDIT", "MANUAL_REPORT");
    private static final List<String> MEETING_ACTIONS = List.of("MEETING_ATTEND", "CHECKIN");
    private static final List<String> TASK_ACTIONS    = List.of("TASK_CREATE", "TASK_COMPLETE");

    private static final BigDecimal MAX_NORM  = new BigDecimal("150");
    private static final BigDecimal TEAM_BASE = new BigDecimal("100");

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ContributionScoreRepository scoreRepository;
    private final ActivityLogRepository activityLogRepository;
    private final WeightConfigRepository weightConfigRepository;
    private final AlertService alertService;

    public ScoreService(ProjectRepository projectRepository,
                        ProjectMemberRepository projectMemberRepository,
                        ContributionScoreRepository scoreRepository,
                        ActivityLogRepository activityLogRepository,
                        WeightConfigRepository weightConfigRepository,
                        AlertService alertService) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.scoreRepository = scoreRepository;
        this.activityLogRepository = activityLogRepository;
        this.weightConfigRepository = weightConfigRepository;
        this.alertService = alertService;
    }

    // ── 전체 프로젝트 일괄 재계산 (스케줄러 호출) ────────────────────────

    @Transactional
    public void recalculateAll() {
        List<Project> projects = projectRepository.findAll();
        for (Project p : projects) {
            try {
                recalculate(p);
            } catch (Exception e) {
                log.error("Score recalculation failed for project {}: {}", p.getId(), e.getMessage());
            }
        }
    }

    // ── 단일 프로젝트 재계산 (수동 트리거 / 스케줄러) ────────────────────

    @Transactional
    public List<ScoreResponse> recalculate(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트 없음: " + projectId));
        return recalculate(project);
    }

    @Transactional
    public List<ScoreResponse> recalculate(Project project) {
        List<ProjectMember> members = projectMemberRepository.findByProject(project);
        if (members.isEmpty()) return List.of();

        // 프로젝트별 가중치 조회 (없으면 기본값)
        BigDecimal[] weights = resolveWeights(project);
        BigDecimal wGit = weights[0], wDoc = weights[1], wMeeting = weights[2], wTask = weights[3];

        // 멤버별 raw 카운트 수집
        Map<UUID, long[]> rawMap = new LinkedHashMap<>();
        for (ProjectMember pm : members) {
            User u = pm.getUser();
            long git     = activityLogRepository.countByProjectAndUserAndTypes(project, u, GIT_ACTIONS);
            long doc     = activityLogRepository.countByProjectAndUserAndTypes(project, u, DOC_ACTIONS);
            long meeting = activityLogRepository.countByProjectAndUserAndTypes(project, u, MEETING_ACTIONS);
            long task    = activityLogRepository.countByProjectAndUserAndTypes(project, u, TASK_ACTIONS);
            rawMap.put(u.getId(), new long[]{git, doc, meeting, task});
        }

        // 카테고리별 팀 평균
        double[] avgs = teamAverages(rawMap.values(), members.size());

        // 정규화 후 저장
        OffsetDateTime now = OffsetDateTime.now();
        List<ContributionScore> savedScores = new ArrayList<>();

        for (ProjectMember pm : members) {
            User u = pm.getUser();
            long[] raw = rawMap.get(u.getId());

            BigDecimal gitN     = normalize(raw[0], avgs[0]);
            BigDecimal docN     = normalize(raw[1], avgs[1]);
            BigDecimal meetingN = normalize(raw[2], avgs[2]);
            BigDecimal taskN    = normalize(raw[3], avgs[3]);

            BigDecimal total = gitN.multiply(wGit)
                    .add(docN.multiply(wDoc))
                    .add(meetingN.multiply(wMeeting))
                    .add(taskN.multiply(wTask))
                    .setScale(2, RoundingMode.HALF_UP);

            ContributionScore score = scoreRepository.findByProjectAndUser(project, u)
                    .orElseGet(() -> { ContributionScore s = new ContributionScore(); s.setProject(project); s.setUser(u); return s; });

            score.setGitScore(gitN);
            score.setDocScore(docN);
            score.setMeetingScore(meetingN);
            score.setTaskScore(taskN);
            score.setTotalScore(total);
            score.setWeightGit(wGit);
            score.setWeightDoc(wDoc);
            score.setWeightMeeting(wMeeting);
            score.setWeightTask(wTask);
            score.setCalculatedAt(now);
            savedScores.add(scoreRepository.save(score));
        }

        // 경보 감지
        alertService.checkAlerts(project, members, savedScores);

        return savedScores.stream().map(ScoreResponse::from).toList();
    }

    // ── 조회 ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScoreResponse> getScores(UUID projectId, User user) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트 없음: " + projectId));
        return scoreRepository.findByProjectOrderByTotalScoreDesc(project).stream()
                .map(ScoreResponse::from)
                .toList();
    }

    // ── private helpers ───────────────────────────────────────────────────

    private BigDecimal[] resolveWeights(Project project) {
        return weightConfigRepository.findTopByProjectOrderByUpdatedAtDesc(project)
                .map(wc -> new BigDecimal[]{wc.getWeightGit(), wc.getWeightDoc(), wc.getWeightMeeting(), wc.getWeightTask()})
                .orElse(new BigDecimal[]{
                        new BigDecimal("0.30"), new BigDecimal("0.25"),
                        new BigDecimal("0.20"), new BigDecimal("0.25")
                });
    }

    private double[] teamAverages(Collection<long[]> rawList, int memberCount) {
        double[] sums = new double[4];
        for (long[] r : rawList) {
            sums[0] += r[0]; sums[1] += r[1]; sums[2] += r[2]; sums[3] += r[3];
        }
        return new double[]{sums[0] / memberCount, sums[1] / memberCount,
                            sums[2] / memberCount, sums[3] / memberCount};
    }

    private BigDecimal normalize(long raw, double avg) {
        if (avg <= 0) return BigDecimal.ZERO;
        BigDecimal normalized = BigDecimal.valueOf(raw)
                .divide(BigDecimal.valueOf(avg), 4, RoundingMode.HALF_UP)
                .multiply(TEAM_BASE)
                .setScale(2, RoundingMode.HALF_UP);
        // 상한 150 클리핑
        return normalized.compareTo(MAX_NORM) > 0 ? MAX_NORM : normalized;
    }
}
