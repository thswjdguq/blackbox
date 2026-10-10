package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.DeliverableConfirmationRepository;
import com.blackbox.repository.DeliverableSubmissionRepository;
import com.blackbox.service.DeliverableStatuses.Status;
import com.blackbox.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 실제 PostgreSQL에서 K-20 2장의 상태 표를 확인한다. 한 건씩 읽을 때와 프로젝트를 한 번에 읽을 때가 같아야 한다. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({DeliverableStatuses.class, ProjectAccessChecker.class})
class DeliverableStatusesTest {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-17T21:10:00+09:00");

    @Autowired EntityManager em;
    @Autowired DeliverableStatuses statuses;
    @Autowired DeliverableStatusReader reader;
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

    /** 업무 명령이 쓰는 읽기 인터페이스(K-20 6장). 프로젝트와 제출물이 맞아야 하고, 쓰기용만 제출물 행을 잠근다. */
    @Test void readerReportsStatusAndLocksOnlyForWrite() {
        User leader = user(em, "leader");
        Project project = project(em, leader), other = project(em, leader);
        Deliverable draft = deliverable(em, project, "초안"), confirmed = with(deliverable(em, project, "확정"), leader, "APPROVED");
        confirm(confirmed, leader);
        em.flush(); em.clear();

        assertEquals("DRAFT", reader.statusOf(project.getId(), draft.getId()));
        assertEquals("CONFIRMED", reader.statusOf(project.getId(), confirmed.getId()));
        assertNotEquals(LockModeType.PESSIMISTIC_WRITE, em.getLockMode(em.find(Deliverable.class, confirmed.getId())));
        em.clear();
        assertEquals("CONFIRMED", reader.lockForWrite(project.getId(), confirmed.getId()));
        assertEquals(LockModeType.PESSIMISTIC_WRITE, em.getLockMode(em.find(Deliverable.class, confirmed.getId())));

        assertTrue(DeliverableStatusReader.isConfirmed("CONFIRMED") && DeliverableStatusReader.isConfirmed("SUBMITTED"));
        assertFalse(DeliverableStatusReader.isConfirmed("DRAFT") || DeliverableStatusReader.isConfirmed("IN_REVIEW"));
        // 다른 프로젝트의 제출물, 없는 제출물, 없는 프로젝트는 모두 없는 것이다
        assertThrows(NotFoundException.class, () -> reader.statusOf(other.getId(), confirmed.getId()));
        assertThrows(NotFoundException.class, () -> reader.lockForWrite(other.getId(), confirmed.getId()));
        assertThrows(NotFoundException.class, () -> reader.statusOf(project.getId(), UUID.randomUUID()));
        assertThrows(NotFoundException.class, () -> reader.statusOf(UUID.randomUUID(), confirmed.getId()));
    }

    /** 트랜잭션 밖에서 잠그면 잠금이 바로 풀려 아무것도 지키지 못한다. 조용히 넘어가지 않고 예외가 난다. */
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lockingOutsideATransactionIsRefused() {
        assertThrows(IllegalTransactionStateException.class, () -> reader.lockForWrite(UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(IllegalTransactionStateException.class, () -> statuses.lockEditable(null, UUID.randomUUID()));
        assertThrows(NotFoundException.class, () -> reader.statusOf(UUID.randomUUID(), UUID.randomUUID()), "읽기는 트랜잭션 없이도 된다");
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
