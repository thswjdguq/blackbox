package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.*;
import com.blackbox.repository.EmailOutboxRepository;
import com.blackbox.scheduler.ScoreScheduler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** V39의 실제 대기 저장·중복·롤백 검사. worker/SMTP는 없으며 모델·메일을 호출하지 않는다. */
@SpringBootTest(properties = {"app.mail.enabled=true", "spring.datasource.hikari.minimum-idle=0"})
@ActiveProfiles("test") @Transactional
class EmailOutboxQueueDatabaseTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService notifications;
    @Autowired EmailOutboxQueue queue;
    @Autowired MailNotificationProperties settings;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean EmailOutboxRepository repository;
    @MockitoBean ScoreScheduler scheduler;
    @MockitoBean EmailDeliveryAuthorization authorization;

    private record Team(Project project, User leader, User worker, User peer, User observer, User outsider) {}
    private static class SyntheticQueueFailure extends RuntimeException {
        SyntheticQueueFailure() { super("synthetic queue failure"); }
    }

    @BeforeEach void configure() { settings.setEnabled(true); settings.setRecipientAllowlist(List.of()); }

    @Test void onlyNewCompletionRequestsCreateOnePendingSnapshot() {
        Team t = team(); Task task = task(t); em.flush();
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        assertEquals(1, count("user_notifications", t.project().getId()));
        assertEquals(1, count("notification_email_outbox", t.project().getId()));
        var row = jdbc.queryForMap("SELECT * FROM notification_email_outbox WHERE project_id=?", t.project().getId());
        assertEquals("PENDING", row.get("status")); assertEquals(0, ((Number) row.get("attempt_count")).intValue());
        assertEquals(task.getId(), row.get("task_id")); assertEquals(t.worker().getId(), row.get("recipient_id"));
        assertEquals(t.leader().getId(), row.get("actor_id")); assertEquals(task.getTitle(), row.get("target_title"));
        assertNull(row.get("deliverable_id")); assertNull(row.get("review_id"));
        assertEquals(row.get("created_at"), row.get("available_at"));
        task.setCompletionRequestedAt(task.getCompletionRequestedAt().plusSeconds(1)); em.flush();
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        assertEquals(2, count("user_notifications", t.project().getId()));
        assertEquals(2, count("notification_email_outbox", t.project().getId()), "보완 후 새 요청은 별도 사건이다");
    }

    @Test void reviewRequestsAreQueuedButVersionAndOrdinaryTaskNewsAreNot() {
        Team t = team(); Task task = task(t);
        FileVault file = file(em, t.project(), t.leader(), "합성 파일.txt", 1, 's');
        Deliverable d = deliverable(em, t.project(), "합성 최종 보고서");
        ReviewRound r = round(em, d, 1, t.leader(), null); em.flush();
        notifications.fileUploaded(task, file, t.leader());
        notifications.reviewEvent("REVIEW_VERSION_POSTED", "VERSION:" + r.getId(), r, null, t.leader(), List.of(t.worker()));
        assertEquals(0, count("notification_email_outbox", t.project().getId()));
        notifications.reviewEvent("REVIEW_REQUESTED", "REVIEW_REQUESTED:" + r.getId(), r, null,
                t.leader(), List.of(t.worker(), t.peer(), t.observer(), t.leader()));
        notifications.reviewEvent("REVIEW_REQUESTED", "REVIEW_REQUESTED:" + r.getId(), r, null,
                t.leader(), List.of(t.worker()));
        assertEquals(1, count("notification_email_outbox", t.project().getId()));
        var row = jdbc.queryForMap("SELECT * FROM notification_email_outbox WHERE project_id=?", t.project().getId());
        assertEquals("REVIEW_REQUESTED", row.get("event_type")); assertEquals(d.getId(), row.get("deliverable_id"));
        assertEquals(r.getId(), row.get("review_id")); assertNull(row.get("task_id"));
        assertEquals(d.getTitle(), row.get("target_title"));
        assertEquals(5, count("user_notifications", t.project().getId()), "허용 목록 밖 팀원의 앱 알림은 유지한다");
    }

    @Test void disabledOrEmptyAllowlistDoesNotQueueOrBackfillOldNotifications() {
        Team t = team(); Task task = task(t); em.flush();
        settings.setEnabled(false);
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        assertEquals(1, count("user_notifications", t.project().getId()));
        assertEquals(0, count("notification_email_outbox", t.project().getId()));
        settings.setEnabled(true);
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        assertEquals(0, count("notification_email_outbox", t.project().getId()), "다시 켜도 기존 앱 알림을 소급 예약하지 않는다");
        settings.setRecipientAllowlist(List.of());
        task.setCompletionRequestedAt(task.getCompletionRequestedAt().plusSeconds(1)); em.flush();
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        assertEquals(2, count("user_notifications", t.project().getId()));
        assertEquals(0, count("notification_email_outbox", t.project().getId()));
        settings.setRecipientAllowlist(List.of(t.worker().getEmail()));
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        assertEquals(0, count("notification_email_outbox", t.project().getId()));
        task.setCompletionRequestedAt(task.getCompletionRequestedAt().plusSeconds(1)); em.flush();
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        assertEquals(1, count("notification_email_outbox", t.project().getId()));
    }

    @Test void actorObserverForeignAndRemovedMembersAreRejectedAtTheQueueBoundary() {
        Team t = team(); Task task = task(t); em.flush();
        settings.setRecipientAllowlist(List.of(t.leader().getEmail(), t.worker().getEmail(), t.observer().getEmail(), t.outsider().getEmail()));
        OffsetDateTime at = OffsetDateTime.now();
        for (User recipient : List.of(t.leader(), t.observer(), t.outsider())) {
            queue.task("TASK_COMPLETION_REQUESTED", "BOUNDARY:" + recipient.getId(), task, t.leader(), recipient, at);
        }
        Project other = project(em, t.outsider()); member(em, other, t.outsider(), "LEADER"); em.flush();
        queue.task("TASK_COMPLETION_REQUESTED", "FOREIGN", task, t.leader(), t.outsider(), at);
        em.createQuery("delete from ProjectMember m where m.project=:p and m.user=:u")
                .setParameter("p", t.project()).setParameter("u", t.worker()).executeUpdate(); em.flush();
        queue.task("TASK_COMPLETION_REQUESTED", "REMOVED", task, t.leader(), t.worker(), at);
        assertEquals(0, count("notification_email_outbox", t.project().getId()));
    }

    @Test void unverifiedAddressNeverQueuesAndVerificationDoesNotBackfillTheOldRequest() {
        Team t = team(); Task task = task(t); em.flush();
        when(authorization.current(t.worker().getId())).thenReturn(Optional.of(
                new EmailDeliveryAuthorization.State(t.worker().getEmail(), true, OffsetDateTime.now().minusDays(1), null)));
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        assertEquals(1, count("user_notifications", t.project().getId()));
        assertEquals(0, count("notification_email_outbox", t.project().getId()));
        when(authorization.current(t.worker().getId())).thenReturn(Optional.of(
                new EmailDeliveryAuthorization.State(t.worker().getEmail(), true,
                        OffsetDateTime.now().minusDays(1), OffsetDateTime.now().minusSeconds(1))));
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        assertEquals(0, count("notification_email_outbox", t.project().getId()));
        task.setCompletionRequestedAt(task.getCompletionRequestedAt().plusSeconds(1)); em.flush();
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        assertEquals(1, count("notification_email_outbox", t.project().getId()));
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failureAfterActualOutboxInsertRollsBackTaskNotificationAndOutbox() {
        var projectId = new AtomicReference<UUID>();
        var tx = new TransactionTemplate(transactions);
        SyntheticQueueFailure failure = assertThrows(SyntheticQueueFailure.class, () -> tx.execute(status -> {
            // MANDATORY인 저장소의 spy 설정도 실제 트랜잭션 안에서 한다.
            doAnswer(call -> {
                call.callRealMethod(); em.flush();
                assertEquals(1, count("notification_email_outbox", projectId.get()));
                assertEquals(1, count("user_notifications", projectId.get()));
                throw new SyntheticQueueFailure();
            }).when(repository).insertIgnoringDuplicate(any());
            Team t = team(); projectId.set(t.project().getId()); Task task = task(t); em.flush();
            notifications.completionRequested(task, t.leader(), List.of(t.worker()));
            return null;
        }));
        assertEquals("synthetic queue failure", failure.getMessage());
        assertEquals(0, count("tasks", projectId.get()));
        assertEquals(0, count("user_notifications", projectId.get()));
        assertEquals(0, count("notification_email_outbox", projectId.get()));
    }

    private Team team() {
        User leader = person("합성 팀장"), worker = person("합성 담당자"), peer = person("합성 팀원");
        User observer = person("합성 관찰자"), outsider = person("합성 외부인");
        Project p = project(em, leader); member(em, p, leader, "LEADER"); member(em, p, worker, "MEMBER");
        member(em, p, peer, "MEMBER"); member(em, p, observer, "OBSERVER");
        settings.setRecipientAllowlist(List.of(worker.getEmail()));
        for (User u : List.of(leader, worker, peer, observer, outsider)) {
            when(authorization.current(u.getId())).thenReturn(Optional.of(new EmailDeliveryAuthorization.State(
                    u.getEmail(), true, OffsetDateTime.now().minusDays(1), OffsetDateTime.now().minusDays(1))));
        }
        return new Team(p, leader, worker, peer, observer, outsider);
    }
    private User person(String name) { User u = user(em, name); u.setEmail(UUID.randomUUID() + "@example.invalid"); return u; }
    private Task task(Team t) {
        Task task = new Task(); task.setProject(t.project()); task.setCreatedBy(t.leader()); task.setTitle("합성 요청 업무");
        task.setStatus("IN_PROGRESS"); task.setPriority("MEDIUM"); task.setCompletionRequestedBy(t.leader());
        task.setCompletionRequestedAt(OffsetDateTime.now()); em.persist(task); return task;
    }
    private int count(String table, UUID project) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE project_id=?", Integer.class, project);
    }
}
