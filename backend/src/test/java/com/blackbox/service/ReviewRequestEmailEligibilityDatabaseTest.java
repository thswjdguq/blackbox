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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 커밋된 합성 자료를 A의 실제 조회/판정으로 읽는다. 파일 본문과 SMTP는 사용하지 않는다. */
@SpringBootTest(properties={"app.mail.enabled=true","spring.datasource.hikari.minimum-idle=0"})
@ActiveProfiles("test")
class ReviewRequestEmailEligibilityDatabaseTest {
    @Autowired ReviewRequestEmailEligibility eligibility;
    @Autowired ReviewRoundService producer;
    @Autowired MailNotificationProperties settings;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ScoreScheduler scoreScheduler;
    record Team(UUID project, UUID deliverable, UUID review, UUID actor, UUID uploader, UUID recipient,
                UUID observer, UUID outsider, UUID file, String email) {}

    @Test void actualProducerNotificationCanBeRecheckedWithoutReconstructingItsEventKey() {
        Team t=team(true);
        UserNotification stored=new TransactionTemplate(transactions).execute(tx -> {
            var opened=producer.open(t.project(),t.deliverable(),new com.blackbox.dto.ReviewDtos.OpenRequest(t.file()),
                    em.find(User.class,t.actor()));
            return em.createQuery("select n from UserNotification n where n.projectId=:p and n.recipientId=:u and n.reviewId=:r and n.type='REVIEW_REQUESTED'",UserNotification.class)
                    .setParameter("p",t.project()).setParameter("u",t.recipient()).setParameter("r",opened.id()).getSingleResult();
        });
        try {
            assertEquals(Optional.of(t.email()),eligibility.currentRecipient(stored));
        } finally {
            // 실제 생산자의 예약도 본인 합성 프로젝트에 한해서 종결한다. 다른 claim 검사의 자료와 섞지 않는다.
            jdbc.update("UPDATE notification_email_outbox SET status='SKIPPED' WHERE project_id=? AND status='PENDING'",t.project());
        }
    }

    @Test void onlyIndependentContributorsReceiveAndCheckingWritesNothing() {
        Team t=team(true);
        var before=jdbc.queryForObject("SELECT opened_at FROM review_rounds WHERE id=?",OffsetDateTime.class,t.review());
        assertEquals(Optional.of(t.email()),eligibility.currentRecipient(snapshot(t,t.recipient())));
        for (UUID excluded:List.of(t.actor(),t.uploader(),t.observer(),t.outsider()))
            assertTrue(eligibility.currentRecipient(snapshot(t,excluded)).isEmpty());
        assertEquals(before,jdbc.queryForObject("SELECT opened_at FROM review_rounds WHERE id=?",OffsetDateTime.class,t.review()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM user_notifications WHERE project_id=?",Integer.class,t.project()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM notification_email_outbox WHERE project_id=?",Integer.class,t.project()));
    }

    @Test void currentAddressAndSettingsAreRechecked() {
        Team t=team(true); var request=snapshot(t,t.recipient());
        settings.setEnabled(false); assertTrue(eligibility.currentRecipient(request).isEmpty());
        settings.setEnabled(true); settings.setRecipientAllowlist(List.of()); assertTrue(eligibility.currentRecipient(request).isEmpty());
        settings.setRecipientAllowlist(List.of(t.email()));
        String changed=UUID.randomUUID()+"@example.invalid";
        tx(() -> em.find(User.class,t.recipient()).setEmail(changed));
        assertTrue(eligibility.currentRecipient(request).isEmpty());
        settings.setRecipientAllowlist(List.of(changed));
        assertEquals(Optional.of(changed),eligibility.currentRecipient(request));
    }

    @Test void observerConversionAndLeavingTheTeamAreRejected() {
        Team t=team(true); var request=snapshot(t,t.recipient());
        tx(() -> em.createQuery("update ProjectMember m set m.role='OBSERVER' where m.project.id=:p and m.user.id=:u")
                .setParameter("p",t.project()).setParameter("u",t.recipient()).executeUpdate());
        assertTrue(eligibility.currentRecipient(request).isEmpty());
        tx(() -> em.createQuery("delete from ProjectMember m where m.project.id=:p and m.user.id=:u")
                .setParameter("p",t.project()).setParameter("u",t.recipient()).executeUpdate());
        assertTrue(eligibility.currentRecipient(request).isEmpty());
    }

    @Test void oldRoundIsRejectedAfterANewRoundOpens() {
        Team t=team(true); var old=snapshot(t,t.recipient());
        UUID newId=new TransactionTemplate(transactions).execute(tx -> {
            ReviewRound next=new ReviewRound(); next.setProject(em.find(Project.class,t.project()));
            next.setDeliverable(em.find(Deliverable.class,t.deliverable())); next.setOpenedBy(em.find(User.class,t.actor()));
            next.setFile(em.find(FileVault.class,t.file())); next.setRoundNo(2); em.persist(next); em.flush(); return next.getId();
        });
        assertTrue(eligibility.currentRecipient(old).isEmpty());
        var current=snapshot(t,t.recipient()); current.setReviewId(newId);
        assertTrue(eligibility.currentRecipient(current).isPresent());
    }

    @Test void approvedAndRevisionRequestedRoundsAreNoLongerPending() {
        for (String decision:List.of("APPROVED","CHANGES_REQUESTED")) {
            Team t=team(true); var request=snapshot(t,t.recipient());
            tx(() -> em.find(ReviewRound.class,t.review()).decide(decision,em.find(User.class,t.recipient())));
            assertTrue(eligibility.currentRecipient(request).isEmpty());
        }
    }

    @Test void absentForeignOrMismatchedTargetsAreRejected() {
        Team t=team(true); var request=snapshot(t,t.recipient());
        request.setReviewId(UUID.randomUUID()); assertTrue(eligibility.currentRecipient(request).isEmpty());
        request=snapshot(t,t.recipient()); request.setDeliverableId(UUID.randomUUID()); assertTrue(eligibility.currentRecipient(request).isEmpty());
        UUID other=new TransactionTemplate(transactions).execute(tx -> {
            Project p=project(em,em.find(User.class,t.actor())); member(em,p,em.find(User.class,t.recipient()),"MEMBER"); em.flush(); return p.getId();
        });
        request=snapshot(t,t.recipient()); request.setProjectId(other); assertTrue(eligibility.currentRecipient(request).isEmpty());
        request=snapshot(t,t.recipient()); request.setActorId(t.outsider()); assertTrue(eligibility.currentRecipient(request).isEmpty());
        request=snapshot(t,t.recipient()); request.setRecipientId(UUID.randomUUID()); assertTrue(eligibility.currentRecipient(request).isEmpty());
    }

    @Test void filelessIntermediateReviewUsesTheProducersSameUploaderRule() {
        Team t=team(false);
        assertTrue(eligibility.currentRecipient(snapshot(t,t.recipient())).isPresent());
        // 파일이 없으면 업로더가 아니다. 임의로 해당 팀원의 결정을 막는 새 규칙을 만들지 않는다.
        assertTrue(eligibility.currentRecipient(snapshot(t,t.uploader())).isPresent());
    }

    private Team team(boolean withFile) {
        Team t=new TransactionTemplate(transactions).execute(tx -> {
            User actor=person("합성 요청자"), uploader=person("합성 업로더"), recipient=person("합성 확인자");
            User observer=person("합성 관찰자"), outsider=person("합성 외부인");
            Project p=project(em,actor); member(em,p,actor,"LEADER"); member(em,p,uploader,"MEMBER");
            member(em,p,recipient,"MEMBER"); member(em,p,observer,"OBSERVER");
            Deliverable d=deliverable(em,p,"합성 검토 제출물");
            FileVault f=withFile?file(em,p,uploader,"합성-결과.txt",1,'a'):null;
            ReviewRound r=new ReviewRound(); r.setProject(p); r.setDeliverable(d); r.setRoundNo(1);
            r.setOpenedBy(actor); r.setFile(f); em.persist(r); em.flush();
            return new Team(p.getId(),d.getId(),r.getId(),actor.getId(),uploader.getId(),recipient.getId(),observer.getId(),outsider.getId(),f==null?null:f.getId(),recipient.getEmail());
        });
        settings.setEnabled(true);
        var addresses=new TransactionTemplate(transactions).execute(tx -> List.of(t.actor(),t.uploader(),t.recipient(),t.observer(),t.outsider())
                .stream().map(id -> em.find(User.class,id).getEmail()).toList());
        settings.setRecipientAllowlist(addresses); return t;
    }
    private User person(String name) { User u=user(em,name); u.setEmail(UUID.randomUUID()+"@example.invalid"); return u; }
    private void tx(Runnable work) { new TransactionTemplate(transactions).executeWithoutResult(tx -> work.run()); }
    private UserNotification snapshot(Team t,UUID recipient) {
        UserNotification n=new UserNotification(); n.setType("REVIEW_REQUESTED"); n.setProjectId(t.project());
        n.setActorId(t.actor()); n.setRecipientId(recipient); n.setDeliverableId(t.deliverable()); n.setReviewId(t.review());
        n.setEventKey("opaque-synthetic-event-"+UUID.randomUUID()); return n;
    }
}
