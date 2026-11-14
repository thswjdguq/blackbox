package com.blackbox.service;

import com.blackbox.entity.Project;
import com.blackbox.repository.ProjectMemberRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** K-40 4.1: 활동 로그가 없던 과거 검토도 도메인에 남은 시각으로 확인한다. */
@Service
@RequiredArgsConstructor
public class ScoreRecentActivityReader {
    private final EntityManager em;
    private final ProjectMemberRepository members;

    /** 현재 기여자의 마지막 활동 시각이다. 활동이 없는 사람은 없으며, 재가입 전 기록도 기간 기준에 따라 포함한다. */
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Map<UUID, OffsetDateTime> read(Project project) {
        List<UUID> users = members.findByProject(project).stream().filter(ProjectAccessChecker::canContribute)
                .map(m -> m.getUser().getId()).distinct().toList();
        if (users.isEmpty()) return Map.of();
        Map<UUID, OffsetDateTime> result = new HashMap<>();
        merge(result, """
                select a.user.id, max(a.occurredAt) from ActivityLog a
                where a.project = :project and a.user.id in :users group by a.user.id
                """, project, users);
        merge(result, """
                select r.openedBy.id, max(r.openedAt) from ReviewRound r
                where r.project = :project and r.openedBy.id in :users group by r.openedBy.id
                """, project, users);
        merge(result, """
                select r.decidedBy.id, max(r.decidedAt) from ReviewRound r
                where r.project = :project and r.decidedAt is not null
                and r.decidedBy.id in :users group by r.decidedBy.id
                """, project, users);
        merge(result, """
                select c.author.id, max(c.createdAt) from ReviewComment c
                where c.project = :project and c.author.id in :users group by c.author.id
                """, project, users);
        merge(result, """
                select c.resolvedBy.id, max(c.resolvedAt) from ReviewComment c
                where c.project = :project and c.resolvedAt is not null
                and c.resolvedBy.id in :users group by c.resolvedBy.id
                """, project, users);
        merge(result, """
                select r.assessedBy.id, max(r.assessedAt) from DeliverableRequirement r
                where r.deliverable.project = :project and r.assessedAt is not null
                and r.assessedBy.id in :users group by r.assessedBy.id
                """, project, users);
        merge(result, """
                select m.author.id, max(m.createdAt) from TaskMessage m
                where m.project = :project and m.author.id in :users group by m.author.id
                """, project, users);
        merge(result, """
                select t.completionApprovedBy.id, max(t.completionApprovedAt) from Task t
                where t.project = :project and t.completionApprovedAt is not null
                and t.completionApprovedBy.id in :users group by t.completionApprovedBy.id
                """, project, users);
        return Map.copyOf(result);
    }

    private void merge(Map<UUID, OffsetDateTime> result, String jpql, Project project, List<UUID> users) {
        for (Object[] row : em.createQuery(jpql, Object[].class).setParameter("project", project)
                .setParameter("users", users).getResultList()) {
            if (row[1] != null) result.merge((UUID) row[0], (OffsetDateTime) row[1],
                    (a, b) -> a.isAfter(b) ? a : b);
        }
    }
}
