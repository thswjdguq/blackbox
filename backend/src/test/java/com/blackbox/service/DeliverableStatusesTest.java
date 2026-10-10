package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.DeliverableConfirmationRepository;
import com.blackbox.repository.DeliverableSubmissionRepository;
import com.blackbox.service.DeliverableStatuses.Status;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.*;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 실제 PostgreSQL에서 K-20 2장의 상태 표를 확인한다. 한 건씩 읽을 때와 프로젝트를 한 번에 읽을 때가 같아야 한다. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(DeliverableStatuses.class)
class DeliverableStatusesTest {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-17T21:10:00+09:00");

    @Autowired EntityManager em;
    @Autowired DeliverableStatuses statuses;
    @Autowired DeliverableConfirmationRepository confirmations;
    @Autowired DeliverableSubmissionRepository submissions;

    @Test void statusFollowsTheTableOneByOneAndForTheWholeProject() {
        User leader = user(em, "leader");
        Project project = project(em, leader), other = project(em, leader);
        Map<Deliverable, Status> expected = new LinkedHashMap<>();

        expected.put(deliverable(em, project, "회차 없음"), new Status("DRAFT", null));
        expected.put(with(deliverable(em, project, "결정 전"), leader, (String) null), new Status("IN_REVIEW", null));
        expected.put(with(deliverable(em, project, "수정 요청"), leader, "CHANGES_REQUESTED"), new Status("DRAFT", "CHANGES_REQUESTED"));
        expected.put(with(deliverable(em, project, "승인"), leader, "APPROVED"), new Status("IN_REVIEW", "APPROVED"));
        expected.put(with(deliverable(em, project, "수정 요청 뒤 새 회차"), leader, "CHANGES_REQUESTED", null), new Status("IN_REVIEW", null));
        expected.put(with(deliverable(em, project, "승인 뒤 수정 요청"), leader, "APPROVED", "CHANGES_REQUESTED"), new Status("DRAFT", "CHANGES_REQUESTED"));
        Deliverable confirmed = with(deliverable(em, project, "확정"), leader, "CHANGES_REQUESTED", "APPROVED");
        Deliverable submitted = with(deliverable(em, project, "제출 기록"), leader, "APPROVED");
        // 다른 프로젝트의 제출물은 그 프로젝트로 물었을 때만 나온다
        Deliverable foreign = with(deliverable(em, other, "남의 제출물"), leader, "APPROVED");
        for (Deliverable d : List.of(confirmed, submitted, foreign)) confirm(d, leader);
        submissions.save(DeliverableSubmission.of(confirmations.getReferenceById(submitted.getId()), null, NOW, null, leader, NOW));
        expected.put(confirmed, new Status("CONFIRMED", "APPROVED"));
        expected.put(submitted, new Status("SUBMITTED", "APPROVED"));
        em.flush(); em.clear();

        expected.forEach((d, status) -> assertEquals(status, statuses.of(d), d.getTitle()));
        Map<UUID, Status> byId = new HashMap<>();
        expected.forEach((d, status) -> byId.put(d.getId(), status));
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertEquals(byId, statuses.of(project, expected.keySet()));
        assertEquals(3, statistics.getPrepareStatementCount(), "제출물 수만큼 조회가 늘면 안 된다");
        assertEquals(Map.of(foreign.getId(), new Status("CONFIRMED", "APPROVED")), statuses.of(other, List.of(foreign)));
    }

    /** 결정을 오래된 회차부터 차례로 넣는다. null은 결정 전이다. */
    private Deliverable with(Deliverable deliverable, User opener, String... decisions) {
        for (int i = 0; i < decisions.length; i++) round(em, deliverable, i + 1, opener, decisions[i]);
        return deliverable;
    }

    private void confirm(Deliverable deliverable, User leader) {
        ReviewRound approved = em.createQuery("select r from ReviewRound r where r.deliverable = :d order by r.roundNo desc", ReviewRound.class)
                .setParameter("d", deliverable).setMaxResults(1).getSingleResult();
        confirmations.save(DeliverableConfirmation.of(approved, leader, NOW));
    }
}
