package com.blackbox.service;

import com.blackbox.entity.ContributionScore;
import com.blackbox.entity.ContributionScoreItem;
import com.blackbox.entity.ContributionScoreItem.Detail;
import com.blackbox.entity.Project;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** K-40 0.3의 첫 기준. 회의/액션 아이템 판정 결함을 바꾸는 후속 PR은 기준 버전을 올려야 한다. */
@Service @RequiredArgsConstructor
public class ScoreSnapshotService {
    public static final String CRITERIA_VERSION = "2026-11-v1";
    private final EntityManager em;
    private final ScoreReviewActivityReader reviews;

    public record Extra(long fileUploads, ScoreReviewActivityReader.Counts reviews) {
        public static final Extra EMPTY = new Extra(0, new ScoreReviewActivityReader.Counts(0, 0, 0, 0, 0, 0));
    }

    /** 계산 트랜잭션에서 프로젝트별로 한 번 읽는다. 사용자마다 파일/검토 전체를 다시 읽지 않는다. */
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Map<UUID, Extra> read(Project project) {
        var activity = reviews.read(project);
        if (activity.isEmpty()) return Map.of();
        Map<UUID, Long> files = new HashMap<>();
        for (Object[] row : em.createQuery("""
                select f.uploader.id, count(distinct f.fileName) from FileVault f
                where f.project = :project group by f.uploader.id
                """, Object[].class).setParameter("project", project).getResultList()) {
            files.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        Map<UUID, Extra> result = new HashMap<>();
        activity.forEach((id, counts) -> result.put(id, new Extra(files.getOrDefault(id, 0L), counts)));
        return Map.copyOf(result);
    }

    /** 방금 정한 불리언을 그대로 항목의 passed에 써서 판정과 항목의 정의가 갈라지지 않게 한다. */
    public static List<ContributionScoreItem> items(ContributionScore score, Extra extra,
                                                  long taskDone, long checkedIn, long meetings, long actionDone) {
        var r = extra.reviews();
        return List.of(
                criterion("TASK_DONE", "업무 완료", "담당한 업무 중 완료한 업무 수(회의 할 일 포함)",
                        taskDone, null, score.isTaskParticipated()),
                criterion("MEETING_ATTENDANCE", "회의 참석", "프로젝트 전체 회의 중 체크인한 수. 회의 수의 절반 이상이면 충족",
                        checkedIn, meetings, score.isMeetingParticipated()),
                criterion("FILE_UPLOAD", "파일 업로드", "올린 파일의 서로 다른 이름 수. 같은 이름의 새 버전은 한 번으로 셈",
                        extra.fileUploads(), null, score.isFileParticipated()),
                criterion("ACTION_ITEM", "회의 할 일", "회의에서 나온 할 일 중 완료한 수",
                        actionDone, null, score.isActionParticipated()),
                new ContributionScoreItem("REVIEW_ACTIVITY", "검토·피드백 참여",
                        "회차 결정, 피드백 작성·해결, 충족 확인, 업무 의견·답글, 업무 결과 확인의 합. 판정에는 넣지 않음",
                        r.total(), null, r.total() > 0, false, List.of(
                        new Detail("REVIEW_DECISION", "회차 결정", r.reviewDecisions()),
                        new Detail("FEEDBACK_WRITTEN", "피드백 작성", r.feedbackWritten()),
                        new Detail("FEEDBACK_RESOLVED", "피드백 해결", r.feedbackResolved()),
                        new Detail("REQUIREMENT_ASSESSED", "요구사항 충족 확인", r.requirementsAssessed()),
                        new Detail("TASK_FEEDBACK", "업무 대화의 의견·답글", r.taskFeedback()),
                        new Detail("TASK_RESULT_REVIEW", "업무 결과 확인", r.taskResultReview()))));
    }

    private static ContributionScoreItem criterion(String code, String label, String description,
                                                   long count, Long total, boolean passed) {
        return new ContributionScoreItem(code, label, description, count, total, passed, true, null);
    }
}
