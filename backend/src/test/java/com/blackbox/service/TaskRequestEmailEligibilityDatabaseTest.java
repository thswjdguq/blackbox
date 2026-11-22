package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.*;
import com.blackbox.scheduler.ScoreScheduler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 커밋된 합성 자료를 독립 읽기 트랜잭션에서 재확인한다. 이메일 전송은 없다. */
@SpringBootTest(properties={"app.mail.enabled=true", "spring.datasource.hikari.minimum-idle=0"})
@ActiveProfiles("test")
class TaskRequestEmailEligibilityDatabaseTest {
    @Autowired TaskRequestEmailEligibility eligibility;
    @Autowired MailNotificationProperties settings;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ScoreScheduler scoreScheduler;
    record Team(UUID project, UUID actor, UUID assignee, UUID recipient, UUID outsider, UUID task, OffsetDateTime requestedAt, String email) {}

    @Test void pendingRequestAllowsTheCurrentIndependentReviewerOnly() {
        Team t=team();
        assertEquals(Optional.of(t.email()), eligibility.currentRecipient(snapshot(t,t.recipient())));
        assertTrue(eligibility.currentRecipient(snapshot(t,t.assignee())).isEmpty());
        assertTrue(eligibility.currentRecipient(snapshot(t,t.actor())).isEmpty());
        assertTrue(eligibility.currentRecipient(snapshot(t,t.outsider())).isEmpty());
    }

    @Test void settingsAndChangedAccountAddressAreReadAgain() {
        Team t=team(); UserNotification request=snapshot(t,t.recipient());
        settings.setEnabled(false); assertTrue(eligibility.currentRecipient(request).isEmpty());
        settings.setEnabled(true); settings.setRecipientAllowlist(List.of()); assertTrue(eligibility.currentRecipient(request).isEmpty());
        settings.setRecipientAllowlist(List.of(t.email()));
        String changed=UUID.randomUUID()+"@example.invalid";
        tx(() -> em.find(User.class,t.recipient()).setEmail(changed));
        assertTrue(eligibility.currentRecipient(request).isEmpty());
        settings.setRecipientAllowlist(List.of(changed));
        assertEquals(Optional.of(changed), eligibility.currentRecipient(request));
        String invalid=UUID.randomUUID()+"\r\n@example.invalid";
        tx(() -> em.find(User.class,t.recipient()).setEmail(invalid));
        settings.setRecipientAllowlist(List.of(invalid));
        assertTrue(eligibility.currentRecipient(request).isEmpty());
    }

    @Test void currentObserverAndRemovedMembershipAreRejected() {
        Team t=team(); UserNotification request=snapshot(t,t.recipient());
        assertTrue(eligibility.currentRecipient(request).isPresent());
        tx(() -> em.createQuery("update ProjectMember m set m.role='OBSERVER' where m.project.id=:p and m.user.id=:u")
                .setParameter("p",t.project()).setParameter("u",t.recipient()).executeUpdate());
        assertTrue(eligibility.currentRecipient(request).isEmpty());
        tx(() -> em.createQuery("delete from ProjectMember m where m.project.id=:p and m.user.id=:u")
                .setParameter("p",t.project()).setParameter("u",t.recipient()).executeUpdate());
        assertTrue(eligibility.currentRecipient(request).isEmpty());
    }

    @Test void cancellationAndResubmissionNeverReviveTheOldEvent() {
        Team t=team(); UserNotification old=snapshot(t,t.recipient());
        change(t, task -> { task.setCompletionRequestedAt(null); task.setCompletionRequestedBy(null); });
        assertTrue(eligibility.currentRecipient(old).isEmpty());
        OffsetDateTime newer=t.requestedAt().plusSeconds(1);
        change(t, task -> { task.setCompletionRequestedAt(newer); task.setCompletionRequestedBy(em.find(User.class,t.actor())); });
        assertTrue(eligibility.currentRecipient(old).isEmpty());
        UserNotification next=snapshot(t,t.recipient()); next.setEventKey("COMPLETION:"+t.task()+":"+NotificationService.micros(newer));
        assertTrue(eligibility.currentRecipient(next).isPresent());
        change(t, task -> task.setCompletionRequestedBy(em.find(User.class,t.assignee())));
        assertTrue(eligibility.currentRecipient(next).isEmpty());
    }

    @Test void approvedOrNonProgressStatesAreNotPendingMailTargets() {
        Team t=team(); UserNotification request=snapshot(t,t.recipient());
        change(t, task -> task.setStatus("TODO")); assertTrue(eligibility.currentRecipient(request).isEmpty());
        change(t, task -> { task.setStatus("DONE"); task.setCompletionApprovedAt(t.requestedAt()); task.setCompletionApprovedBy(em.find(User.class,t.recipient())); });
        assertTrue(eligibility.currentRecipient(request).isEmpty());
        change(t, task -> { task.setStatus("IN_PROGRESS"); task.setCompletionApprovedAt(null); task.setCompletionApprovedBy(null); });
        assertTrue(eligibility.currentRecipient(request).isPresent());
    }

    @Test void missingAndForeignTargetsFailClosed() {
        Team t=team(); UserNotification request=snapshot(t,t.recipient());
        request.setTaskId(UUID.randomUUID()); assertTrue(eligibility.currentRecipient(request).isEmpty());
        request=snapshot(t,t.recipient()); request.setProjectId(UUID.randomUUID()); assertTrue(eligibility.currentRecipient(request).isEmpty());
        UUID other=new TransactionTemplate(transactions).execute(status -> {
            Project p=project(em,em.find(User.class,t.actor())); member(em,p,em.find(User.class,t.recipient()),"MEMBER"); em.flush(); return p.getId();
        });
        request=snapshot(t,t.recipient()); request.setProjectId(other);
        assertTrue(eligibility.currentRecipient(request).isEmpty());
        request=snapshot(t,t.recipient()); request.setRecipientId(UUID.randomUUID()); assertTrue(eligibility.currentRecipient(request).isEmpty());
    }

    @Test void reassignedRecipientCannotReviewAndReadCheckCreatesNoNotifications() {
        Team t=team(); UserNotification request=snapshot(t,t.recipient());
        var before=jdbc.queryForObject("SELECT updated_at FROM tasks WHERE id=?",OffsetDateTime.class,t.task());
        assertTrue(eligibility.currentRecipient(request).isPresent());
        assertEquals(before,jdbc.queryForObject("SELECT updated_at FROM tasks WHERE id=?",OffsetDateTime.class,t.task()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM user_notifications WHERE project_id=?",Integer.class,t.project()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM notification_email_outbox WHERE project_id=?",Integer.class,t.project()));
        tx(() -> em.createQuery("update TaskAssignee a set a.user=:u where a.task.id=:t")
                .setParameter("u",em.find(User.class,t.recipient())).setParameter("t",t.task()).executeUpdate());
        assertTrue(eligibility.currentRecipient(request).isEmpty());
    }

    private Team team() {
        Team t=new TransactionTemplate(transactions).execute(status -> {
            User actor=person("합성 요청자"), assignee=person("합성 담당자"), recipient=person("합성 확인자"), outsider=person("합성 외부인");
            Project p=project(em,actor); member(em,p,actor,"LEADER"); member(em,p,assignee,"MEMBER"); member(em,p,recipient,"MEMBER");
            Task task=new Task(); task.setProject(p); task.setCreatedBy(actor); task.setTitle("발송 재확인 합성 업무");
            task.setStatus("IN_PROGRESS"); task.setPriority("MEDIUM"); task.setCompletionCriteria("출처가 있는 비교표");
            OffsetDateTime at=OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
            task.setCompletionRequestedAt(at); task.setCompletionRequestedBy(actor); em.persist(task);
            TaskAssignee assignment=new TaskAssignee(); assignment.setTask(task); assignment.setUser(assignee); em.persist(assignment); em.flush();
            return new Team(p.getId(),actor.getId(),assignee.getId(),recipient.getId(),outsider.getId(),task.getId(),at,recipient.getEmail());
        });
        settings.setEnabled(true);
        // 다른 배정/요청자 제외가 단순히 허용 목록 밖이라서 통과하는 것이 아닌지 확인한다.
        var addresses=new TransactionTemplate(transactions).execute(status -> List.of(t.actor(),t.assignee(),t.recipient(),t.outsider()).stream()
                .map(id -> em.find(User.class,id).getEmail()).toList());
        settings.setRecipientAllowlist(addresses); return t;
    }
    private User person(String name) { User u=user(em,name); u.setEmail(UUID.randomUUID()+"@example.invalid"); return u; }
    private void tx(Runnable action) { new TransactionTemplate(transactions).executeWithoutResult(status -> action.run()); }
    private void change(Team t, Consumer<Task> change) { tx(() -> change.accept(em.find(Task.class,t.task()))); }
    private UserNotification snapshot(Team t, UUID recipient) {
        UserNotification request=new UserNotification(); request.setType("TASK_COMPLETION_REQUESTED"); request.setProjectId(t.project());
        request.setActorId(t.actor()); request.setRecipientId(recipient); request.setTaskId(t.task());
        request.setEventKey("COMPLETION:"+t.task()+":"+NotificationService.micros(t.requestedAt())); return request;
    }
}
