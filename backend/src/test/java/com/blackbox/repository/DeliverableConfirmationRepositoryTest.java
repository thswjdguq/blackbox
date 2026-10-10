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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

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
        User leader = user("leader"), member = user("member");
        Project project = project(leader);
        Deliverable report = deliverable(project, "중간 보고서");
        ReviewRound approved = round(report, 2, leader);

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

    @Test void databaseRejectsWhatTheServiceMustNeverWrite() {
        User leader = user("leader");
        Project project = project(leader), other = project(leader);
        Deliverable report = deliverable(project, "중간 보고서"), poster = deliverable(project, "포스터"), foreign = deliverable(other, "남의 제출물");
        ReviewRound round = round(report, 1, leader), posterRound = round(poster, 1, leader), foreignRound = round(foreign, 1, leader);
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
        User leader = user("leader");
        Deliverable report = deliverable(project(leader), "중간 보고서");
        ReviewRound first = round(report, 1, leader), second = round(report, 2, leader);
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

    private ReviewRound round(Deliverable deliverable, int no, User opener) {
        ReviewRound round = new ReviewRound(); round.setProject(deliverable.getProject()); round.setDeliverable(deliverable);
        round.setRoundNo(no); round.setOpenedBy(opener); round.decide("APPROVED", opener); em.persist(round);
        return round;
    }

    private Deliverable deliverable(Project project, String title) {
        Deliverable d = new Deliverable(); d.setProject(project); d.setTitle(title); d.setDueDate(LocalDate.of(2026, 10, 18));
        em.persist(d); return d;
    }

    private Project project(User creator) {
        Project project = new Project(); project.setName("confirmation-" + UUID.randomUUID()); project.setCreatedBy(creator);
        em.persist(project); return project;
    }

    private User user(String name) {
        User user = new User(); user.setEmail(name + "-" + UUID.randomUUID() + "@example.com"); user.setName(name);
        user.setPasswordHash("not-a-real-hash"); user.setRole("STUDENT"); em.persist(user);
        return user;
    }
}
