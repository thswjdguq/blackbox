package com.blackbox.service;

import com.blackbox.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 기존 스키마와 실제 PostgreSQL의 FK·종류 제약을 거친 자료로 K-40 검토 횟수를 확인한다. 자료는 롤백된다. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ScoreReviewActivityReader.class)
class ScoreReviewActivityReaderTest {
    @Autowired EntityManager em;
    @Autowired ScoreReviewActivityReader reader;

    @Test void allSixKindsAreScopedToProjectAndCurrentContributors() {
        User a = user(em, "합성 검토자"), b = user(em, "합성 팀장");
        Project p = project(em, b); member(em, p, a, "MEMBER"); member(em, p, b, "LEADER");
        User idle = user(em, "합성 기록없음"), observer = user(em, "합성 관찰자");
        member(em, p, idle, "MEMBER"); member(em, p, observer, "OBSERVER");
        Seed s = seed(p, a, b);
        comment(s.round(), observer, null);
        Project other = project(em, b); member(em, other, a, "MEMBER"); member(em, other, b, "LEADER");
        seed(other, a, b); // 같은 사람의 다른 프로젝트 기록은 어느 항목에도 섞이지 않아야 한다.
        em.flush(); em.clear();

        var result = reader.read(p);
        assertEquals(3, result.size()); assertFalse(result.containsKey(observer.getId()));
        assertEquals(new ScoreReviewActivityReader.Counts(1, 2, 1, 1, 3, 3), result.get(a.getId()));
        assertEquals(11, result.get(a.getId()).total());
        assertEquals(new ScoreReviewActivityReader.Counts(0, 0, 1, 0, 0, 0), result.get(b.getId()));
        assertEquals(new ScoreReviewActivityReader.Counts(0, 0, 0, 0, 0, 0), result.get(idle.getId()));
        assertEquals(0L, em.createQuery("select count(s) from ContributionScore s", Long.class).getSingleResult());
    }

    @Test void reopeningClearingAndTaskDeletionReduceCurrentFactsButKeepRejections() {
        User a = user(em, "합성 검토자"), b = user(em, "합성 팀장");
        Project p = project(em, b); member(em, p, a, "MEMBER"); member(em, p, b, "LEADER");
        Seed s = seed(p, a, b); em.flush();
        var before = reader.read(p).get(a.getId());

        s.resolvedByA().setResolvedBy(null); s.resolvedByA().setResolvedAt(null);
        s.requirement().clearAssessment();
        s.task().setStatus("IN_PROGRESS"); s.task().setCompletedAt(null);
        s.task().setCompletionApprovedBy(null); s.task().setCompletionApprovedAt(null);
        em.flush();
        var reopened = reader.read(p).get(a.getId());
        assertEquals(0, reopened.feedbackResolved()); assertEquals(0, reopened.requirementsAssessed());
        assertEquals(2, reopened.taskResultReview(), "업무 승인만 빠지고 두 보완 요청 이력은 남는다");
        assertEquals(3, before.taskResultReview(), "앞서 읽은 값은 이후 상태 변경으로 바뀌지 않는다");

        s.resolvedByA().setResolvedBy(b); s.resolvedByA().setResolvedAt(OffsetDateTime.now()); em.flush();
        assertEquals(2, reader.read(p).get(b.getId()).feedbackResolved(), "재해결한 사람이 집계된다");
        em.createQuery("delete from Task t where t.id = :id").setParameter("id", s.task().getId()).executeUpdate();
        em.clear();
        var deleted = reader.read(p).get(a.getId());
        assertEquals(0, deleted.taskFeedback(), "업무 삭제의 DB cascade로 사라진 대화는 빠진다");
        assertEquals(2, deleted.taskResultReview(), "업무 삭제 뒤에도 이미 수행한 보완 요청 이력은 남는다");
    }

    @Test void roleChangesAndRejoinUseCurrentMembershipButIncludePastCounts() {
        User a = user(em, "합성 검토자"), b = user(em, "합성 팀장");
        Project p = project(em, b); ProjectMember membership = member(em, p, a, "MEMBER");
        member(em, p, b, "LEADER"); seed(p, a, b); em.flush();
        membership.setRole("OBSERVER"); em.flush();
        assertFalse(reader.read(p).containsKey(a.getId()));
        em.remove(membership); em.flush();
        ProjectMember rejoined = member(em, p, a, "MEMBER"); em.flush(); em.refresh(rejoined);
        assertTrue(rejoined.getJoinedAt().isAfter(OffsetDateTime.parse("2026-01-01T00:00:00Z")));
        assertEquals(2, reader.read(p).get(a.getId()).feedbackWritten(), "지금 가입 전의 피드백도 횟수에 포함한다");

        Project observersOnly = project(em, b); member(em, observersOnly, b, "OBSERVER");
        em.flush(); assertEquals(Map.of(), reader.read(observersOnly));
    }

    private record Seed(ReviewRound round, Task task, ReviewComment resolvedByA, DeliverableRequirement requirement) {}

    private Seed seed(Project p, User a, User b) {
        Deliverable d = deliverable(em, p, "합성 제출물");
        ReviewRound r = round(em, d, 1, b, null); r.decide("APPROVED", a);
        ReviewComment resolvedByA = comment(r, a, a); comment(r, a, b);
        DeliverableRequirement req = requirement(em, d, true, a);
        Task t = new Task(); t.setProject(p); t.setCreatedBy(b); t.setTitle("합성 업무");
        t.setStatus("DONE"); t.setPriority("MEDIUM"); t.setCompletedAt(OffsetDateTime.now());
        t.setCompletionApprovedBy(a); t.setCompletionApprovedAt(OffsetDateTime.now()); em.persist(t);
        message(t, a, "COMMENT", null, null); message(t, a, "COMMENT", null, null);
        TaskMessage request = message(t, b, "REQUEST", a, null);
        message(t, a, "REPLY", null, request); message(t, a, "REQUEST", b, null);
        log(p, a, "TASK_COMPLETION_REJECT"); log(p, a, "TASK_COMPLETION_REJECT");
        log(p, a, "TASK_COMPLETION_CANCEL");
        return new Seed(r, t, resolvedByA, req);
    }

    private ReviewComment comment(ReviewRound r, User author, User resolver) {
        ReviewComment c = new ReviewComment(); c.setProject(r.getProject()); c.setDeliverable(r.getDeliverable());
        c.setReview(r); c.setAuthor(author); c.setContent("합성 피드백");
        c.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        if (resolver != null) { c.setResolvedBy(resolver); c.setResolvedAt(OffsetDateTime.now()); }
        em.persist(c); return c;
    }

    private TaskMessage message(Task t, User author, String kind, User recipient, TaskMessage parent) {
        TaskMessage m = new TaskMessage(); m.setProject(t.getProject()); m.setTask(t); m.setAuthor(author);
        m.setKind(kind); m.setBody("합성 대화"); m.setRecipient(recipient); m.setParent(parent);
        m.setCreatedAt(OffsetDateTime.now()); em.persist(m); return m;
    }

    private void log(Project p, User actor, String type) {
        ActivityLog log = new ActivityLog(); log.setProject(p); log.setUser(actor); log.setActionType(type);
        log.setSource("PLATFORM"); log.setTrustLevel(BigDecimal.ONE); log.setOccurredAt(OffsetDateTime.now());
        em.persist(log);
    }
}
