package com.blackbox.repository;

import com.blackbox.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 실제 PostgreSQL에서 확정과 제출 기록의 매핑, 그리고 서비스 검증을 우회해도 DB가 막는 것을 확인한다(K-20 7장). */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DeliverableConfirmationRepositoryTest {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-17T21:10:00+09:00");

    @Autowired EntityManager em;
    @Autowired DeliverableConfirmationRepository confirmations;
    @Autowired DeliverableSubmissionRepository submissions;

    @Test void confirmationAndSubmissionAreStoredAndReadBack() {
        User leader = user(em, "leader"), member = user(em, "member");
        Project project = project(em, leader);
        Deliverable report = deliverable(em, project, "중간 보고서");
        ReviewRound approved = round(em, report, 2, leader, "APPROVED");

        confirmations.saveAndFlush(DeliverableConfirmation.of(approved, leader, NOW));
        submissions.saveAndFlush(DeliverableSubmission.of(confirmations.findById(report.getId()).orElseThrow(),
                "학교 LMS 과제함", NOW.plusHours(12), "접수 번호 2026-1234", member, NOW.plusHours(13)));
        em.clear();

        DeliverableConfirmation c = confirmations.findById(report.getId()).orElseThrow();
        assertEquals(approved.getId(), c.getReview().getId());
        assertEquals(project.getId(), c.getProject().getId());
        assertEquals("leader", c.getConfirmedBy().getName());
        assertEquals(NOW.toInstant(), c.getConfirmedAt().toInstant());
        DeliverableSubmission s = submissions.findById(report.getId()).orElseThrow();
        assertEquals("학교 LMS 과제함", s.getChannel());
        assertEquals("접수 번호 2026-1234", s.getNote());
        assertEquals("member", s.getRecordedBy().getName());
        assertEquals(NOW.plusHours(12).toInstant(), s.getSubmittedAt().toInstant(), "낸 시각과 기록한 시각은 따로다");
        assertEquals(NOW.plusHours(13).toInstant(), s.getRecordedAt().toInstant());
    }

    /** 방금 만든 기록과 다시 읽은 기록이 글자까지 같아야, 저장 직후의 응답과 이후의 조회가 같다. */
    @Test void recordsAreCreatedInTheShapeTheDatabaseReturns() {
        User leader = user(em, "leader");
        ReviewRound approved = round(em, deliverable(em, project(em, leader), "중간 보고서"), 1, leader, "APPROVED");
        OffsetDateTime precise = OffsetDateTime.parse("2026-10-17T21:10:00.123456789+09:00");
        DeliverableConfirmation created = confirmations.saveAndFlush(DeliverableConfirmation.of(approved, leader, precise));
        DeliverableSubmission recorded = submissions.saveAndFlush(
                DeliverableSubmission.of(created, "  학교 LMS 과제함 ", precise.minusHours(1), "   ", leader, precise.plusHours(1)));
        em.clear();

        DeliverableConfirmation confirmation = confirmations.findById(created.getDeliverableId()).orElseThrow();
        assertEquals(created.getConfirmedAt(), confirmation.getConfirmedAt());
        assertEquals("2026-10-17T12:10:00.123456Z", created.getConfirmedAt().toString());
        DeliverableSubmission submission = submissions.findById(created.getDeliverableId()).orElseThrow();
        assertEquals(recorded.getSubmittedAt(), submission.getSubmittedAt());
        assertEquals(recorded.getRecordedAt(), submission.getRecordedAt());
        assertEquals("학교 LMS 과제함", submission.getChannel());
        assertNull(submission.getNote(), "비어 있는 글은 없는 값으로 남는다");
        assertTrue(submission.sameContentAs(DeliverableSubmission.of(confirmation, "학교 LMS 과제함",
                precise.minusHours(1).withOffsetSameInstant(ZoneOffset.ofHours(-5)), null, user(em, "other"), precise)));
        assertFalse(submission.sameContentAs(DeliverableSubmission.of(confirmation, "이메일", precise.minusHours(1), null, leader, precise)));
        assertFalse(submission.sameContentAs(DeliverableSubmission.of(confirmation, "학교 LMS 과제함", precise, null, leader, precise)));
        assertFalse(submission.sameContentAs(DeliverableSubmission.of(confirmation, "학교 LMS 과제함", precise.minusHours(1), "메모", leader, precise)));
    }

    @Test void databaseRejectsWhatTheServiceMustNeverWrite() {
        User leader = user(em, "leader");
        Project project = project(em, leader), other = project(em, leader);
        Deliverable report = deliverable(em, project, "중간 보고서"), poster = deliverable(em, project, "포스터"), foreign = deliverable(em, other, "남의 제출물");
        ReviewRound round = round(em, report, 1, leader, "APPROVED"), posterRound = round(em, poster, 1, leader, "APPROVED"), foreignRound = round(em, foreign, 1, leader, "APPROVED");
        confirmations.saveAndFlush(DeliverableConfirmation.of(round, leader, NOW));
        String confirm = "insert into deliverable_confirmations (deliverable_id, project_id, review_id, confirmed_by, confirmed_at) values (?, ?, ?, ?, now())";
        String submit = "insert into deliverable_submissions (deliverable_id, submitted_at, recorded_by, recorded_at) values (?, now(), ?, now())";

        String pk = "deliverable_confirmations_pkey", review = "fk_confirmation_review", confirmed = "deliverable_submissions_deliverable_id_fkey";

        rejected("같은 제출물을 두 번 확정", pk, () -> sql(confirm, report.getId(), project.getId(), round.getId(), leader.getId()));
        rejected("다른 제출물의 회차로 확정", review, () -> sql(confirm, poster.getId(), project.getId(), round.getId(), leader.getId()));
        rejected("다른 프로젝트의 회차로 확정", review, () -> sql(confirm, poster.getId(), project.getId(), foreignRound.getId(), leader.getId()));
        rejected("프로젝트를 다르게 적어 확정", review, () -> sql(confirm, poster.getId(), other.getId(), posterRound.getId(), leader.getId()));
        rejected("확정하지 않은 제출물에 제출 기록", confirmed, () -> sql(submit, poster.getId(), leader.getId()));
        rejected("확정에 쓰인 회차 삭제", review, () -> sql("delete from review_rounds where id = ?", round.getId()));

        sql(submit, report.getId(), leader.getId());
        rejected("같은 제출물에 제출 기록 두 번", "deliverable_submissions_pkey", () -> sql(submit, report.getId(), leader.getId()));
        rejected("제출 기록이 있는 확정 삭제", confirmed, () -> sql("delete from deliverable_confirmations where deliverable_id = ?", report.getId()));
    }

    /** 키를 직접 넣는 엔티티는 저장소의 save가 기존 줄에 합쳐 조용히 넘어간다. 그렇게 되지 않고 DB까지 가서 거절되는지 본다. */
    @Test void savingAgainThroughTheRepositoryIsRejectedNotIgnored() {
        User leader = user(em, "leader");
        Deliverable report = deliverable(em, project(em, leader), "중간 보고서");
        ReviewRound first = round(em, report, 1, leader, "APPROVED"), second = round(em, report, 2, leader, "APPROVED");
        confirmations.saveAndFlush(DeliverableConfirmation.of(first, leader, NOW));
        submissions.saveAndFlush(DeliverableSubmission.of(confirmations.getReferenceById(report.getId()), null, NOW, null, leader, NOW));
        em.clear();

        rejected("저장소로 두 번째 확정", "deliverable_confirmations_pkey",
                () -> confirmations.saveAndFlush(DeliverableConfirmation.of(em.find(ReviewRound.class, second.getId()), leader, NOW.plusDays(1))));
        rejected("저장소로 두 번째 제출 기록", "deliverable_submissions_pkey",
                () -> submissions.saveAndFlush(DeliverableSubmission.of(confirmations.getReferenceById(report.getId()), "다른 곳", NOW, null, leader, NOW)));

        assertEquals(first.getId(), confirmations.findById(report.getId()).orElseThrow().getReview().getId());
        assertNull(submissions.findById(report.getId()).orElseThrow().getChannel());
    }

    /** 제약 위반은 트랜잭션을 못 쓰게 만들므로 저장점으로 감싸 한 건씩 확인한다. 어느 제약에 걸렸는지까지 본다. */
    private void rejected(String what, String constraint, Executable write) {
        em.createNativeQuery("savepoint before_check").executeUpdate();
        String reason = NestedExceptionUtils.getMostSpecificCause(assertThrows(Exception.class, write, what)).getMessage();
        assertTrue(reason.contains(constraint), what + ": " + reason);
        em.clear();   // 실패한 엔티티가 남아 있으면 다음 쿼리가 그것을 다시 넣으려 한다
        em.createNativeQuery("rollback to savepoint before_check").executeUpdate();
    }

    private void sql(String sql, Object... parameters) {
        var query = em.createNativeQuery(sql);
        for (int i = 0; i < parameters.length; i++) query.setParameter(i + 1, parameters[i]);
        query.executeUpdate();
    }
}
