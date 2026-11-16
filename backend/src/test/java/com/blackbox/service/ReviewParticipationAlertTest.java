package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.AlertRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 과거 활동 로그를 만들지 않고 실제 도메인 자료로 K-40 4장의 경보를 검증한다. 합성 자료는 롤백된다. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AlertService.class, ScoreRecentActivityReader.class, ScoreReviewActivityReader.class, ProjectAccessChecker.class})
class ReviewParticipationAlertTest {
    @Autowired EntityManager em;
    @Autowired AlertService alerts;
    @Autowired AlertRepository repository;
    @Autowired ScoreRecentActivityReader recent;
    @Autowired ScoreReviewActivityReader reviews;
    @MockitoBean DiscordNotificationService discord;

    record Team(Project project, User leader, User worker, List<ProjectMember> members) {}
    private Team team() {
        User leader = user(em, "합성 팀장"), worker = user(em, "합성 검토자");
        Project p = project(em, leader);
        var roster = List.of(member(em, p, leader, "LEADER"), member(em, p, worker, "MEMBER"));
        // joined_at은 DB 생성·불변 매핑이므로 setter만 쓰면 clear 뒤 실제 가입 시각과 어긋난다.
        em.flush();
        em.createNativeQuery("update project_members set joined_at = :joined where project_id = :project")
                .setParameter("joined", OffsetDateTime.now().minusDays(40)).setParameter("project", p.getId()).executeUpdate();
        roster.forEach(em::refresh);
        return new Team(p, leader, worker, roster);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOG", "OPEN", "DECIDE", "WRITE", "RESOLVE", "ASSESS", "REQUEST", "APPROVE"})
    void eachSupportedSourcePreventsDropoutWithoutBackfilledLogs(String kind) {
        Team t = team(); OffsetDateTime time = OffsetDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MICROS);
        source(t, t.worker(), kind, time); em.flush();
        assertEquals(time.toInstant(), recent.read(t.project()).get(t.worker().getId()).toInstant());
        check(t);
        assertFalse(active(t, t.worker(), "DROPOUT"));
        long logCount = em.createQuery("select count(a) from ActivityLog a where a.project = :p", Long.class)
                .setParameter("p", t.project()).getSingleResult();
        assertEquals(kind.equals("LOG") ? 1L : 0L, logCount, "과거 도메인 활동을 로그로 소급하지 않는다");
    }

    @Test void taskFeedbackResolvesBothAlertsWithoutChangingNoneAndDeletionRestoresEligibility() {
        Team t = team(); Alert free = alert(t, "FREE_RIDE"), dropout = alert(t, "DROPOUT");
        Task task = task(t); message(t, task, t.worker(), "COMMENT", OffsetDateTime.now()); em.flush();
        ContributionScore score = score(t, t.worker());
        alerts.checkAlerts(t.project(), t.members(), List.of(score)); em.flush();
        assertNotNull(free.getResolvedAt()); assertEquals("검토에 참여했습니다", free.getResolveReason());
        assertNotNull(dropout.getResolvedAt()); assertEquals("NONE", score.getParticipationLevel());
        assertFalse(score.isTaskParticipated()); assertFalse(score.isMeetingParticipated());
        assertFalse(score.isFileParticipated()); assertFalse(score.isActionParticipated());
        assertFalse(active(t, t.worker(), "FREE_RIDE")); assertFalse(active(t, t.worker(), "DROPOUT"));

        em.createQuery("delete from Task t where t.id = :id").setParameter("id", task.getId()).executeUpdate();
        em.clear(); check(t); em.flush();
        assertTrue(active(t, t.worker(), "FREE_RIDE")); assertTrue(active(t, t.worker(), "DROPOUT"));
    }

    @Test void requestOnlyIsRecentActivityButDoesNotCountAsGivingFeedback() {
        Team t = team(); source(t, t.worker(), "REQUEST", OffsetDateTime.now()); em.flush();
        assertEquals(0, reviews.read(t.project()).get(t.worker().getId()).total());
        check(t); em.flush();
        assertTrue(active(t, t.worker(), "FREE_RIDE")); assertFalse(active(t, t.worker(), "DROPOUT"));
    }

    @Test void reviewEventResolvesFreeRideEvenBeforeFirstScoreRowExists() {
        Team t = team(); Alert free = alert(t, "FREE_RIDE");
        message(t, task(t), t.worker(), "COMMENT", OffsetDateTime.now()); em.flush();
        alerts.reevaluate(t.worker(), t.project());
        assertNotNull(free.getResolvedAt()); assertEquals("검토에 참여했습니다", free.getResolveReason());
        assertEquals(0L, em.createQuery("select count(s) from ContributionScore s where s.project = :p", Long.class)
                .setParameter("p", t.project()).getSingleResult());
    }

    @Test void newMemberStillGetsTheExistingFourteenDayGracePeriod() {
        Team t = team(); ProjectMember worker = t.members().get(1);
        em.createNativeQuery("update project_members set joined_at = :joined where id = :id")
                .setParameter("joined", OffsetDateTime.now().minusDays(2)).setParameter("id", worker.getId()).executeUpdate();
        em.refresh(worker); check(t);
        assertFalse(active(t, t.worker(), "DROPOUT")); assertTrue(active(t, t.worker(), "FREE_RIDE"));
    }

    @Test void betweenSevenAndFourteenDaysNeitherCreatesNorResolvesDropoutThenRecentActivityResolves() {
        Team t = team(); source(t, t.worker(), "REQUEST", OffsetDateTime.now().minusDays(9)); em.flush();
        check(t); assertFalse(active(t, t.worker(), "DROPOUT"));
        Alert existing = alert(t, "DROPOUT"); check(t); assertNull(existing.getResolvedAt());
        source(t, t.worker(), "REQUEST", OffsetDateTime.now().minusDays(6)); em.flush();
        alerts.reevaluate(t.worker(), t.project()); assertNotNull(existing.getResolvedAt());
    }

    @Test void otherProjectsAndNonContributorsAreExcludedAndOldActivityStillCreatesDropout() {
        Team t = team(); User observer = user(em, "합성 관찰자"), former = user(em, "합성 탈퇴자");
        member(em, t.project(), observer, "OBSERVER");
        source(t, observer, "WRITE", OffsetDateTime.now()); source(t, former, "WRITE", OffsetDateTime.now());
        Project other = project(em, t.leader()); member(em, other, t.worker(), "MEMBER");
        Team elsewhere = new Team(other, t.leader(), t.worker(), List.of());
        source(elsewhere, t.worker(), "WRITE", OffsetDateTime.now());
        source(t, t.worker(), "REQUEST", OffsetDateTime.now().minusDays(16)); em.flush();
        var result = recent.read(t.project());
        assertFalse(result.containsKey(observer.getId())); assertFalse(result.containsKey(former.getId()));
        assertTrue(result.get(t.worker().getId()).isBefore(OffsetDateTime.now().minusDays(14)));
        check(t); em.flush(); assertTrue(active(t, t.worker(), "DROPOUT"));
        assertTrue(repository.findActiveByProject(t.project()).stream()
                .noneMatch(a -> a.getUser().equals(observer) || a.getUser().equals(former)));
    }

    @Test void clearingCurrentResolutionAssessmentAndApprovalRemovesThoseActivityTimes() {
        Team t = team(); Deliverable d = deliverable(em, t.project(), "합성 결과");
        ReviewRound r = round(em, d, 1, t.leader(), null);
        ReviewComment c = comment(t, r, t.leader(), OffsetDateTime.now().minusDays(30));
        c.setResolvedBy(t.worker()); c.setResolvedAt(OffsetDateTime.now());
        DeliverableRequirement req = requirement(em, d, true, t.worker());
        Task task = task(t); task.setStatus("DONE"); task.setCompletionApprovedBy(t.worker()); task.setCompletionApprovedAt(OffsetDateTime.now());
        em.flush(); assertTrue(recent.read(t.project()).containsKey(t.worker().getId()));
        c.setResolvedAt(null); c.setResolvedBy(null); req.clearAssessment();
        task.setStatus("IN_PROGRESS"); task.setCompletionApprovedAt(null); task.setCompletionApprovedBy(null);
        em.flush(); assertFalse(recent.read(t.project()).containsKey(t.worker().getId()));
    }

    private void check(Team t) {
        alerts.checkAlerts(t.project(), t.members(), List.of(score(t, t.worker())));
    }
    private ContributionScore score(Team t, User u) {
        ContributionScore s = new ContributionScore(); s.setProject(t.project()); s.setUser(u); s.setCalculatedAt(OffsetDateTime.now());
        return s;
    }
    private Alert alert(Team t, String type) {
        Alert a = new Alert(); a.setProject(t.project()); a.setUser(t.worker()); a.setAlertType(type);
        a.setSeverity("MEDIUM"); a.setMessage("합성 경보"); em.persist(a); return a;
    }
    private boolean active(Team t, User u, String type) {
        return repository.existsByProjectAndUserAndAlertTypeAndResolvedAtIsNull(t.project(), u, type);
    }
    private Task task(Team t) {
        Task task = new Task(); task.setProject(t.project()); task.setCreatedBy(t.leader());
        task.setTitle("합성 업무"); task.setStatus("IN_PROGRESS"); task.setPriority("MEDIUM"); em.persist(task); return task;
    }
    private void message(Team t, Task task, User actor, String kind, OffsetDateTime time) {
        TaskMessage m = new TaskMessage(); m.setProject(t.project()); m.setTask(task); m.setAuthor(actor);
        m.setKind(kind); m.setBody("합성 대화"); m.setCreatedAt(time);
        if (kind.equals("REQUEST")) m.setRecipient(t.leader()); em.persist(m);
    }
    private ReviewComment comment(Team t, ReviewRound r, User actor, OffsetDateTime time) {
        ReviewComment c = new ReviewComment(); c.setProject(t.project()); c.setDeliverable(r.getDeliverable());
        c.setReview(r); c.setAuthor(actor); c.setContent("합성 피드백"); c.setCreatedAt(time); em.persist(c); return c;
    }
    private void source(Team t, User actor, String kind, OffsetDateTime time) {
        if (kind.equals("LOG")) {
            ActivityLog log = new ActivityLog(); log.setProject(t.project()); log.setUser(actor);
            log.setActionType("TASK_COMPLETION_CANCEL"); log.setSource("PLATFORM");
            log.setTrustLevel(BigDecimal.ONE); log.setOccurredAt(time); em.persist(log); return;
        }
        if (kind.equals("REQUEST")) { message(t, task(t), actor, "REQUEST", time); return; }
        if (kind.equals("APPROVE")) {
            Task task = task(t); task.setStatus("DONE"); task.setCompletionApprovedBy(actor); task.setCompletionApprovedAt(time); return;
        }
        Deliverable d = deliverable(em, t.project(), "합성 결과");
        if (kind.equals("ASSESS")) { DeliverableRequirement req = requirement(em, d, true, actor); req.setAssessedAt(time); return; }
        ReviewRound r = round(em, d, 1, kind.equals("OPEN") ? actor : t.leader(), null);
        r.setOpenedAt(OffsetDateTime.now().minusDays(30));
        switch (kind) {
            case "OPEN" -> r.setOpenedAt(time);
            case "DECIDE" -> { r.decide("APPROVED", actor); r.setDecidedAt(time); }
            case "WRITE" -> comment(t, r, actor, time);
            case "RESOLVE" -> { ReviewComment c = comment(t, r, t.leader(), OffsetDateTime.now().minusDays(30)); c.setResolvedBy(actor); c.setResolvedAt(time); }
            default -> throw new IllegalArgumentException(kind);
        }
    }
}
