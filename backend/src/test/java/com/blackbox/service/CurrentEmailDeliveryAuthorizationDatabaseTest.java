package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.dto.ReviewDtos;
import com.blackbox.entity.*;
import com.blackbox.repository.EmailDeliveryRepository;
import com.blackbox.scheduler.ScoreScheduler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 실제 인증 Reader·수신 설정·업무/검토 생산자·큐·worker 결합. 외부 SMTP만 대역이다. */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0")
@ActiveProfiles("test")
class CurrentEmailDeliveryAuthorizationDatabaseTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MailNotificationProperties mail;
    @Autowired EmailNotificationSettingsService preferences;
    @Autowired EmailDeliveryAuthorization authorization;
    @Autowired ObjectProvider<EmailDeliveryAuthorization> authorizations;
    @Autowired ObjectProvider<EmailNotificationTransport> transports;
    @Autowired EmailDeliveryRepository deliveries;
    @Autowired TaskRequestEmailEligibility tasks;
    @Autowired ReviewRequestEmailEligibility reviews;
    @Autowired TaskWorkflowService workflow;
    @Autowired ReviewRoundService rounds;
    @MockitoBean EmailDeliveryWorker scheduledWorker;
    @MockitoBean ScoreScheduler scheduler;
    @MockitoBean EmailNotificationTransport transport;
    final List<UUID> projects = new ArrayList<>();
    EmailDeliveryWorker worker;
    record Team(UUID project, UUID task, UUID actor, UUID assignee, UUID recipient, String address) {}

    @BeforeEach void prepare() {
        mail.setEnabled(true); mail.setRecipientAllowlist(List.of());
        when(transport.send(any(), any(), any())).thenReturn(EmailNotificationTransport.Result.ACCEPTED);
        worker = new EmailDeliveryWorker(mail, deliveries, tasks, reviews, authorizations, transports, "http://localhost:13000");
    }
    @AfterEach void closeOnlyOwnPendingFixtures() {
        for (var id : projects) jdbc.update("UPDATE notification_email_outbox SET status='SKIPPED',sent_at=NULL WHERE project_id=? AND status IN ('PENDING','PROCESSING')", id);
    }

    @Test void optedInButUnverifiedRecipientGetsOnlyAnAppNotification() {
        Team t = team(); preferences.update(t.recipient(), true); request(t);
        assertTrue(authorization.current(t.recipient()).orElseThrow().enabled());
        assertNull(authorization.current(t.recipient()).orElseThrow().verifiedAt());
        assertEquals(0, mailCount(t)); assertEquals(1, appCount(t)); worker.poll(); verifyNoInteractions(transport);
    }

    @Test void verifiedButNotOptedInRecipientGetsOnlyAnAppNotification() {
        Team t = team(); verified(t); request(t);
        assertFalse(authorization.current(t.recipient()).orElseThrow().enabled());
        assertEquals(0, mailCount(t)); assertEquals(1, appCount(t)); worker.poll(); verifyNoInteractions(transport);
    }

    @Test void verifiedOptInAndMixedCaseCurrentAddressAreSentThroughTheRealWorker() {
        Team t = ready(); request(t); assertEquals(1, mailCount(t)); worker.poll();
        assertEquals("SENT", mailStatus(t)); assertEquals(1, appCount(t));
        verify(transport).send(any(), eq(t.address()), argThat(c -> c.target().getQuery().equals("task=" + t.task())));
    }

    @Test void optingOutAfterTheRequestSkipsTheMailAndPreservesTheAppNotification() {
        Team t = ready(); request(t); preferences.update(t.recipient(), false); worker.poll();
        assertEquals("SKIPPED", mailStatus(t)); assertEquals(1, appCount(t)); verifyNoInteractions(transport);
    }

    @Test void reenablingDoesNotResurrectTheOldPendingRequest() {
        Team t = ready(); request(t); preferences.update(t.recipient(), false); preferences.update(t.recipient(), true);
        worker.poll(); assertEquals("SKIPPED", mailStatus(t)); verifyNoInteractions(transport);
    }

    @Test void laterVerificationCannotQualifyAnOldPendingMail() {
        Team t = ready(); request(t);
        jdbc.update("UPDATE users SET email_verified_at=clock_timestamp() WHERE id=?", t.recipient());
        worker.poll(); assertEquals("SKIPPED", mailStatus(t)); verifyNoInteractions(transport);
    }

    @Test void actualReviewProducerUsesTheSameVerifiedPreferenceAndSendsOneRequest() {
        Team t = ready();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Project p = em.find(Project.class, t.project()); User actor = em.find(User.class, t.actor());
            var d = deliverable(em, p, "합성 검토 이메일");
            var f = file(em, p, em.find(User.class, t.assignee()), "synthetic.txt", 1, 'a'); em.flush();
            rounds.open(p.getId(), d.getId(), new ReviewDtos.OpenRequest(f.getId()), actor);
        });
        assertEquals(1, mailCount(t)); worker.poll(); assertEquals("SENT", mailStatus(t));
        verify(transport).send(any(), eq(t.address()), argThat(c -> c.target().getPath().contains("/deliverables/")));
    }

    private Team ready() { Team t = team(); verified(t); preferences.update(t.recipient(), true); return t; }
    private void verified(Team t) { jdbc.update("UPDATE users SET email_verified_at=now()-interval '1 day' WHERE id=?", t.recipient()); }
    private void request(Team t) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> workflow.request(t.project(), t.task(), em.find(User.class, t.actor())));
    }
    private Team team() {
        Team value = new TransactionTemplate(transactions).execute(tx -> {
            User actor = user(em, "합성 요청자"), assignee = user(em, "합성 담당자"), recipient = user(em, "합성 확인자");
            recipient.setEmail("MixedLocal-" + UUID.randomUUID() + "@Example.TEST");
            Project p = project(em, actor); projects.add(p.getId());
            member(em,p,actor,"LEADER"); member(em,p,assignee,"MEMBER"); member(em,p,recipient,"MEMBER");
            Task task = new Task(); task.setProject(p); task.setCreatedBy(actor); task.setTitle("합성 현재 인증 연결");
            task.setStatus("IN_PROGRESS"); task.setPriority("MEDIUM"); em.persist(task);
            TaskAssignee assignment = new TaskAssignee(); assignment.setTask(task); assignment.setUser(assignee); em.persist(assignment); em.flush();
            return new Team(p.getId(),task.getId(),actor.getId(),assignee.getId(),recipient.getId(),recipient.getEmail());
        });
        mail.setRecipientAllowlist(List.of(value.address())); return value;
    }
    private int mailCount(Team t) { return jdbc.queryForObject("SELECT count(*) FROM notification_email_outbox WHERE project_id=?",Integer.class,t.project()); }
    private int appCount(Team t) { return jdbc.queryForObject("SELECT count(*) FROM user_notifications WHERE project_id=?",Integer.class,t.project()); }
    private String mailStatus(Team t) { return jdbc.queryForObject("SELECT status FROM notification_email_outbox WHERE project_id=?",String.class,t.project()); }
}
