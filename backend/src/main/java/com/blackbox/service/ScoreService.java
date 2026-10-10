package com.blackbox.service;

import com.blackbox.dto.ScoreResponse;
import com.blackbox.entity.*;
import com.blackbox.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ScoreService {

    private static final Logger log = LoggerFactory.getLogger(ScoreService.class);

    private final ProjectRepository          projectRepository;
    private final ProjectMemberRepository    projectMemberRepository;
    private final ContributionScoreRepository scoreRepository;
    private final MeetingRepository          meetingRepository;
    private final MeetingAttendeeRepository  attendeeRepository;
    private final TaskAssigneeRepository     taskAssigneeRepository;
    private final FileVaultRepository        fileVaultRepository;
    private final AlertService               alertService;
    private final ProjectAccessChecker      accessChecker;

    public ScoreService(ProjectRepository projectRepository,
                        ProjectMemberRepository projectMemberRepository,
                        ContributionScoreRepository scoreRepository,
                        MeetingRepository meetingRepository,
                        MeetingAttendeeRepository attendeeRepository,
                        TaskAssigneeRepository taskAssigneeRepository,
                        FileVaultRepository fileVaultRepository,
                        AlertService alertService,
                        ProjectAccessChecker accessChecker) {
        this.projectRepository       = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.scoreRepository         = scoreRepository;
        this.meetingRepository       = meetingRepository;
        this.attendeeRepository      = attendeeRepository;
        this.taskAssigneeRepository  = taskAssigneeRepository;
        this.fileVaultRepository     = fileVaultRepository;
        this.alertService            = alertService;
        this.accessChecker           = accessChecker;
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
    public List<ScoreResponse> recalculate(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        // HTTP 요청은 참여·쓰기 권한을 먼저 확인한다. 내부 스케줄러 경로와 구별한다.
        accessChecker.requireContributor(project, user);
        return recalculate(project);
    }

    /** 내부 스케줄러·업무 이벤트 전용. HTTP 요청은 사용자 인자를 받는 메서드를 사용한다. */
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

        // 전체 회의 수 (회의 참석율 계산 기준)
        long totalMeetings = meetingRepository.findByProjectOrderByMeetingDateDesc(project).size();

        OffsetDateTime now = OffsetDateTime.now();
        List<ContributionScore> savedScores = new ArrayList<>();

        for (ProjectMember pm : members) {
            User u = pm.getUser();

            // ── 1. 태스크 완료 참여 여부 ──────────────────────────────────
            boolean taskParticipated = taskAssigneeRepository.countDoneByProjectAndUser(project, u) > 0;

            // ── 2. 회의 참석 여부 (50% 이상 체크인) ───────────────────────
            boolean meetingParticipated;
            if (totalMeetings == 0) {
                meetingParticipated = false;
            } else {
                long checkedIn = attendeeRepository.countCheckedInByProjectAndUser(project, u);
                meetingParticipated = (double) checkedIn / totalMeetings >= 0.5;
            }

            // ── 3. 파일 업로드 여부 ───────────────────────────────────────
            boolean fileParticipated = fileVaultRepository.countByProjectAndUploader(project, u) > 0;

            // ── 4. 액션아이템 완료 여부 ───────────────────────────────────
            boolean actionParticipated = taskAssigneeRepository.countDoneActionItemsByProjectAndUser(project, u) > 0;

            // ── 종합 판정 ─────────────────────────────────────────────────
            int count = (taskParticipated ? 1 : 0)
                    + (meetingParticipated ? 1 : 0)
                    + (fileParticipated    ? 1 : 0)
                    + (actionParticipated  ? 1 : 0);

            String level = count == 4 ? "FULL"
                         : count >= 2 ? "PARTIAL"
                         :              "NONE";

            // ── 저장 ──────────────────────────────────────────────────────
            ContributionScore score = scoreRepository.findByProjectAndUser(project, u)
                    .orElseGet(() -> {
                        ContributionScore s = new ContributionScore();
                        s.setProject(project);
                        s.setUser(u);
                        return s;
                    });

            score.setTaskParticipated(taskParticipated);
            score.setMeetingParticipated(meetingParticipated);
            score.setFileParticipated(fileParticipated);
            score.setActionParticipated(actionParticipated);
            score.setParticipationLevel(level);
            score.setCalculatedAt(now);
            savedScores.add(scoreRepository.save(score));
        }

        // 경보 감지
        alertService.checkAlerts(project, members, savedScores);

        return savedScores.stream().map(ScoreResponse::from).toList();
    }

    // ── 태스크 완료 이벤트용 비동기 재계산 ─────────────────────────────────

    @Async
    @Transactional
    public void recalculateAsync(UUID projectId) {
        // 같은 객체의 recalculate 호출은 트랜잭션 프록시를 거치지 않는다.
        // 비동기 작업의 진입점에서 시작해야 지연 조회와 점수·경보 저장이 한 트랜잭션에 속한다.
        // 실패는 밖으로 전달하여 전체 롤백하고 Spring의 비동기 예외 처리기에 기록한다.
        recalculate(projectId);
    }

    // ── 조회 ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScoreResponse> getScores(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        return scoreRepository.findByProjectOrderByTotalScoreDesc(project).stream()
                .map(ScoreResponse::from)
                .toList();
    }
}
