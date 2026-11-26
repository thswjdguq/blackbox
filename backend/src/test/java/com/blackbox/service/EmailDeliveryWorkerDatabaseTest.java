package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.*;
import com.blackbox.repository.EmailDeliveryRepository;
import com.blackbox.scheduler.ScoreScheduler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 실제 생산자·커밋·조건 조회·대기 결과 저장을 연결한다. 인증 조회와 SMTP는 대역이며 실제 인증/메일함 검사가 아니다. */
@SpringBootTest(properties = {"app.mail.enabled=true", "spring.datasource.hikari.minimum-idle=0"})
@ActiveProfiles("test")
class EmailDeliveryWorkerDatabaseTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MailNotificationProperties settings;
    @Autowired TaskWorkflowService workflow;
    @Autowired EmailDeliveryRepository deliveries;
    @Autowired TaskRequestEmailEligibility tasks;
    @Autowired ReviewRequestEmailEligibility reviews;
    @MockitoBean ScoreScheduler scheduler;
    @MockitoBean EmailDeliveryWorker scheduledWorker;
    @MockitoBean EmailDeliveryAuthorization authorization;
    @MockitoBean EmailNotificationTransport transport;
    final List<UUID> projects = new ArrayList<>();
    EmailDeliveryWorker worker;
    record Request(UUID project, UUID task, UUID recipient, String address, UUID delivery) {}

    @BeforeEach void configure() {
        settings.setEnabled(true); settings.setRecipientAllowlist(List.of());
        worker = new EmailDeliveryWorker(settings, deliveries, tasks, reviews,
                new StaticListableBeanFactory(Map.of("authorization", authorization)).getBeanProvider(EmailDeliveryAuthorization.class),
                new StaticListableBeanFactory(Map.of("transport", transport)).getBeanProvider(EmailNotificationTransport.class),
                "http://localhost:13000");
        when(transport.send(any(), any(), any())).thenReturn(EmailNotificationTransport.Result.ACCEPTED);
    }

    @AfterEach void finishOnlyOwnSyntheticRows() {
        for (UUID id : projects) jdbc.update("""
                UPDATE notification_email_outbox SET status='SKIPPED', sent_at=NULL
                WHERE project_id=? AND status IN ('PENDING','PROCESSING')
                """, id);
    }

    @Test void committedProducerNotificationIsSentAndRecordedThroughTheRealRepositories() {
        Request r = request(true); assertNotNull(r.delivery()); worker.poll();
        var row = row(r); assertEquals("SENT", row.get("status"));
        assertEquals(1, ((Number) row.get("attempt_count")).intValue()); assertNotNull(row.get("sent_at"));
        assertEquals(1, appCount(r));
        verify(transport).send(eq(r.delivery()), eq(r.address()), argThat(content ->
                content.target().getQuery().equals("task=" + r.task())));
    }

    @Test void absentRealAuthorizationDoesNotClaimThePendingDelivery() {
        Request r = request(true);
        worker = new EmailDeliveryWorker(settings, deliveries, tasks, reviews,
                new StaticListableBeanFactory().getBeanProvider(EmailDeliveryAuthorization.class),
                new StaticListableBeanFactory(Map.of("transport", transport)).getBeanProvider(EmailNotificationTransport.class),
                "http://localhost:13000");
        worker.poll(); assertEquals("PENDING", row(r).get("status"));
        assertEquals(0, ((Number) row(r).get("attempt_count")).intValue()); verifyNoInteractions(transport);
    }

    @Test void anAllowlistedUnverifiedUserGetsOnlyTheAppNotification() {
        Request r = request(false); assertNull(r.delivery()); worker.poll();
        assertEquals(1, appCount(r)); verifyNoInteractions(transport);
    }

    @Test void optingOutAfterQueuingSkipsTheDeliveryAndKeepsTheAppNotification() {
        Request r = request(true);
        when(authorization.current(r.recipient())).thenReturn(Optional.of(new EmailDeliveryAuthorization.State(
                r.address(), false, OffsetDateTime.now().minusDays(1), OffsetDateTime.now().minusDays(1))));
        worker.poll(); assertEquals("SKIPPED", row(r).get("status")); assertEquals(1, appCount(r));
        verifyNoInteractions(transport);
    }

    @Test void laterVerificationDoesNotResurrectAnAlreadyQueuedOldRequest() {
        Request r = request(true);
        when(authorization.current(r.recipient())).thenReturn(Optional.of(new EmailDeliveryAuthorization.State(
                r.address(), true, OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusSeconds(1))));
        worker.poll(); assertEquals("SKIPPED", row(r).get("status")); verifyNoInteractions(transport);
    }

    @Test void cancellationIsReadFromTheCommittedTaskBeforeSmtp() {
        Request r = request(true);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Task task = em.find(Task.class, r.task()); task.setCompletionRequestedAt(null); task.setCompletionRequestedBy(null);
        });
        worker.poll(); assertEquals("SKIPPED", row(r).get("status")); assertEquals(1, appCount(r));
        verifyNoInteractions(transport);
    }

    @Test void aTemporaryTransportFailureSchedulesOneRetryWithoutLosingTheAppNotification() {
        Request r = request(true); when(transport.send(any(), any(), any())).thenReturn(EmailNotificationTransport.Result.TEMPORARY_FAILURE);
        worker.poll(); var row = row(r); assertEquals("PENDING", row.get("status"));
        assertEquals("TEMPORARY_FAILURE", row.get("error_code")); assertEquals(1, ((Number) row.get("attempt_count")).intValue());
        assertNull(row.get("sent_at")); worker.poll(); verify(transport, times(1)).send(any(), any(), any()); assertEquals(1, appCount(r));
    }

    @Test void aPermanentTransportFailureIsFinalAndDoesNotRollBackTheAppNotification() {
        Request r = request(true); when(transport.send(any(), any(), any())).thenReturn(EmailNotificationTransport.Result.PERMANENT_FAILURE);
        worker.poll(); assertEquals("FAILED", row(r).get("status")); assertNull(row(r).get("sent_at"));
        worker.poll(); verify(transport, times(1)).send(any(), any(), any()); assertEquals(1, appCount(r));
    }

    @Test void authenticationFailureLeavesTheNextMailUnclaimedAcrossPolls() {
        Request first = request(true), next = request(true);
        settings.setRecipientAllowlist(List.of(first.address(), next.address()));
        when(transport.send(any(), any(), any())).thenReturn(EmailNotificationTransport.Result.AUTHENTICATION_FAILURE);
        worker.poll(); worker.poll();
        assertEquals("FAILED", row(first).get("status")); assertEquals("AUTHENTICATION_FAILURE", row(first).get("error_code"));
        assertEquals("PENDING", row(next).get("status")); assertEquals(0, ((Number) row(next).get("attempt_count")).intValue());
        verify(transport, times(1)).send(any(), any(), any());
    }

    @Test void aTemporaryFailureStopsBeforeClaimingTheNextReadyMail() {
        Request first = request(true), next = request(true);
        settings.setRecipientAllowlist(List.of(first.address(), next.address()));
        when(transport.send(any(), any(), any())).thenReturn(EmailNotificationTransport.Result.TEMPORARY_FAILURE);
        worker.poll();
        assertEquals("PENDING", row(first).get("status")); assertEquals(1, ((Number) row(first).get("attempt_count")).intValue());
        assertEquals("PENDING", row(next).get("status")); assertEquals(0, ((Number) row(next).get("attempt_count")).intValue());
        verify(transport, times(1)).send(any(), any(), any());
    }

    private Request request(boolean verified) {
        return new TransactionTemplate(transactions).execute(status -> {
            User actor = person("합성 요청자"), assignee = person("합성 담당자"), recipient = person("합성 확인자");
            Project project = project(em, actor); projects.add(project.getId());
            member(em, project, actor, "LEADER"); member(em, project, assignee, "MEMBER"); member(em, project, recipient, "MEMBER");
            Task task = new Task(); task.setProject(project); task.setCreatedBy(actor); task.setTitle("메일 처리 합성 업무");
            task.setStatus("IN_PROGRESS"); task.setPriority("MEDIUM"); task.setCompletionCriteria("합성 결과 확인");
            em.persist(task);
            TaskAssignee assignment = new TaskAssignee(); assignment.setTask(task); assignment.setUser(assignee); em.persist(assignment); em.flush();
            settings.setRecipientAllowlist(List.of(recipient.getEmail()));
            var past = OffsetDateTime.now().minusDays(1);
            when(authorization.current(recipient.getId())).thenReturn(Optional.of(new EmailDeliveryAuthorization.State(
                    recipient.getEmail(), true, past, verified ? past : null)));
            // 실제 요청 서비스의 저장·refresh·DB 마이크로초 정규화를 그대로 거친다.
            workflow.request(project.getId(), task.getId(), actor);
            List<UUID> ids = jdbc.queryForList("SELECT id FROM notification_email_outbox WHERE project_id=?", UUID.class, project.getId());
            return new Request(project.getId(), task.getId(), recipient.getId(), recipient.getEmail(), ids.isEmpty() ? null : ids.getFirst());
        });
    }

    private Map<String, Object> row(Request r) {
        return jdbc.queryForMap("SELECT * FROM notification_email_outbox WHERE id=?", r.delivery());
    }
    private int appCount(Request r) {
        return jdbc.queryForObject("SELECT count(*) FROM user_notifications WHERE project_id=?", Integer.class, r.project());
    }
    private User person(String name) {
        User u = user(em, name); u.setEmail(UUID.randomUUID() + "@example.invalid"); return u;
    }
}
