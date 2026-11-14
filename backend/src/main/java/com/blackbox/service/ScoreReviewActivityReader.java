package com.blackbox.service;

import com.blackbox.entity.Project;
import com.blackbox.repository.ProjectMemberRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** K-40 3장의 현재 검토 참여 횟수. snapshot 저장·판정·경보는 이 조회 모듈의 책임이 아니다. */
@Service
@RequiredArgsConstructor
public class ScoreReviewActivityReader {
    private final EntityManager em;
    private final ProjectMemberRepository members;

    public record Counts(long reviewDecisions, long feedbackWritten, long feedbackResolved,
                         long requirementsAssessed, long taskFeedback, long taskResultReview) {
        public long total() {
            return reviewDecisions + feedbackWritten + feedbackResolved + requirementsAssessed
                    + taskFeedback + taskResultReview;
        }
    }

    /**
     * 내부 점수 계산용 조회다. 호출자는 프로젝트를 확인하고 계산 트랜잭션을 연 뒤 사용한다.
     * 현재 팀장·팀원만 반환하며, 기록이 없는 사람도 0이다. 횟수는 재가입 전 기록까지 포함한다.
     * 반환값은 읽은 시점의 값으로 고정되며, 이 메서드는 점수/경보를 저장하지 않는다.
     */
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Map<UUID, Counts> read(Project project) {
        List<UUID> users = members.findByProject(project).stream()
                .filter(ProjectAccessChecker::canContribute).map(m -> m.getUser().getId()).distinct().toList();
        if (users.isEmpty()) return Map.of();

        Map<UUID, Long> decisions = count("""
                select r.decidedBy.id, count(r) from ReviewRound r
                where r.project = :project and r.decidedBy.id in :users group by r.decidedBy.id
                """, project, users);
        Map<UUID, Long> written = count("""
                select c.author.id, count(c) from ReviewComment c
                where c.project = :project and c.author.id in :users group by c.author.id
                """, project, users);
        Map<UUID, Long> resolved = count("""
                select c.resolvedBy.id, count(c) from ReviewComment c
                where c.project = :project and c.resolvedAt is not null
                and c.resolvedBy.id in :users group by c.resolvedBy.id
                """, project, users);
        Map<UUID, Long> assessed = count("""
                select r.assessedBy.id, count(r) from DeliverableRequirement r
                where r.deliverable.project = :project and r.assessedAt is not null
                and r.assessedBy.id in :users group by r.assessedBy.id
                """, project, users);
        Map<UUID, Long> feedback = count("""
                select m.author.id, count(m) from TaskMessage m
                where m.project = :project and m.kind in ('COMMENT', 'REPLY')
                and m.author.id in :users group by m.author.id
                """, project, users);
        Map<UUID, Long> approved = count("""
                select t.completionApprovedBy.id, count(t) from Task t
                where t.project = :project and t.status = 'DONE' and t.completionApprovedAt is not null
                and t.completionApprovedBy.id in :users group by t.completionApprovedBy.id
                """, project, users);
        // 보완 요청은 다음 완료 요청 때 업무에서 지워지므로 이 항목만 같은 트랜잭션의 불변 이력을 쓴다(K-40).
        Map<UUID, Long> rejected = count("""
                select a.user.id, count(a) from ActivityLog a
                where a.project = :project and a.actionType = 'TASK_COMPLETION_REJECT'
                and a.user.id in :users group by a.user.id
                """, project, users);

        Map<UUID, Counts> result = new HashMap<>();
        for (UUID user : users) {
            result.put(user, new Counts(value(decisions, user), value(written, user), value(resolved, user),
                    value(assessed, user), value(feedback, user), value(approved, user) + value(rejected, user)));
        }
        return Map.copyOf(result);
    }

    private Map<UUID, Long> count(String jpql, Project project, List<UUID> users) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : em.createQuery(jpql, Object[].class)
                .setParameter("project", project).setParameter("users", users).getResultList()) {
            result.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private static long value(Map<UUID, Long> counts, UUID user) {
        return counts.getOrDefault(user, 0L);
    }
}
