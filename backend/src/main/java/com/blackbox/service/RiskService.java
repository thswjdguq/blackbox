package com.blackbox.service;

import com.blackbox.dto.RiskResponse;
import com.blackbox.entity.Project;
import com.blackbox.entity.Task;
import com.blackbox.entity.User;
import com.blackbox.repository.ActivityLogRepository;
import com.blackbox.repository.ProjectMemberRepository;
import com.blackbox.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RiskService {

    private final ProjectAccessChecker    accessChecker;
    private final TaskRepository          taskRepo;
    private final ActivityLogRepository   logRepo;
    private final ProjectMemberRepository memberRepo;

    public RiskService(ProjectAccessChecker accessChecker,
                       TaskRepository taskRepo,
                       ActivityLogRepository logRepo,
                       ProjectMemberRepository memberRepo) {
        this.accessChecker = accessChecker;
        this.taskRepo      = taskRepo;
        this.logRepo       = logRepo;
        this.memberRepo    = memberRepo;
    }

    @Transactional(readOnly = true)
    public RiskResponse assess(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);

        List<Task>  tasks    = taskRepo.findByProjectOrderByCreatedAtDesc(project);
        LocalDate   today    = LocalDate.now();
        LocalDate   endDate  = project.getEndDate();

        int total    = tasks.size();
        int done     = (int) tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        int overdue  = (int) tasks.stream()
                .filter(t -> !"DONE".equals(t.getStatus())
                        && t.getDueDate() != null
                        && t.getDueDate().isBefore(today))
                .count();
        int completionRate = total > 0 ? (int) Math.round(done * 100.0 / total) : 0;

        Integer daysRemaining = endDate != null
                ? (int) ChronoUnit.DAYS.between(today, endDate) : null;

        // 최근 7일 활동 여부 (전체 멤버 기준)
        boolean recentActivity = memberRepo.findByProject(project).stream().anyMatch(m ->
                logRepo.hasActivityAfter(project, m.getUser(),
                        OffsetDateTime.now().minusDays(7)));

        // ── 위험도 점수 계산 ──────────────────────────────────────────────
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // 1. 완료율 (최대 35점)
        if (total == 0) {
            score += 10;
            reasons.add("등록된 태스크가 없습니다");
        } else if (completionRate < 30) {
            score += 35;
            reasons.add("태스크 완료율이 " + completionRate + "%로 매우 낮습니다");
        } else if (completionRate < 60) {
            score += 20;
            reasons.add("태스크 완료율이 " + completionRate + "%입니다");
        } else if (completionRate < 80) {
            score += 8;
        }

        // 2. 기한 초과 태스크 (최대 30점)
        if (overdue > 0) {
            int overdueScore = Math.min(overdue * 10, 30);
            score += overdueScore;
            reasons.add("기한이 지난 태스크 " + overdue + "개");
        }

        // 3. 남은 기간 (최대 25점)
        if (daysRemaining != null) {
            if (daysRemaining < 0) {
                score += 25;
                reasons.add("프로젝트 마감일이 " + Math.abs(daysRemaining) + "일 초과됐습니다");
            } else if (daysRemaining <= 3) {
                score += 25;
                reasons.add("마감까지 " + daysRemaining + "일 남았습니다");
            } else if (daysRemaining <= 7) {
                score += 18;
                reasons.add("마감까지 " + daysRemaining + "일 남았습니다");
            } else if (daysRemaining <= 14) {
                score += 8;
            }
        }

        // 4. 최근 활동 없음 (10점)
        if (!recentActivity && total > 0) {
            score += 10;
            reasons.add("최근 7일간 팀 활동이 없습니다");
        }

        score = Math.min(score, 100);

        String level = score >= 75 ? "CRITICAL"
                : score >= 50 ? "HIGH"
                : score >= 25 ? "MEDIUM"
                : "LOW";

        if (reasons.isEmpty()) {
            reasons.add("프로젝트가 순조롭게 진행되고 있습니다");
        }

        return new RiskResponse(score, level, total, done, overdue,
                completionRate, daysRemaining, reasons);
    }
}
