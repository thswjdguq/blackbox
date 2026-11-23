package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.*;
import com.blackbox.repository.EmailOutboxRepository;
import com.blackbox.repository.ProjectMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/** 새 앱 알림 저장 직후의 예약 경계. SMTP는 호출하지 않으며 수신 주소도 대기 표에 저장하지 않는다. */
@Service
public class EmailOutboxQueue {
    private final MailNotificationProperties settings;
    private final ProjectMemberRepository members;
    private final EmailOutboxRepository outbox;

    public EmailOutboxQueue(MailNotificationProperties settings, ProjectMemberRepository members, EmailOutboxRepository outbox) {
        this.settings = settings; this.members = members; this.outbox = outbox;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void task(String type, String key, Task task, User actor, User recipient, OffsetDateTime at) {
        if (!"TASK_COMPLETION_REQUESTED".equals(type) || !configured()) return;
        UserNotification snapshot = snapshot(type, key, task.getProject(), actor, recipient, task.getTitle(), at);
        snapshot.setTaskId(task.getId());
        enqueue(snapshot, task.getProject(), recipient);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void review(String type, String key, ReviewRound review, User actor, User recipient, OffsetDateTime at) {
        if (!"REVIEW_REQUESTED".equals(type) || !configured()) return;
        UserNotification snapshot = snapshot(type, key, review.getProject(), actor, recipient, review.getDeliverable().getTitle(), at);
        snapshot.setDeliverableId(review.getDeliverable().getId()); snapshot.setReviewId(review.getId());
        enqueue(snapshot, review.getProject(), recipient);
    }

    private boolean configured() { return settings.isEnabled() && !settings.getRecipientAllowlist().isEmpty(); }

    private void enqueue(UserNotification snapshot, Project project, User recipient) {
        ProjectMember current = members.findByProjectAndUser(project, recipient).orElse(null);
        var policy = new EmailNotificationPolicy(settings.isEnabled(), settings.getRecipientAllowlist());
        if (policy.permits(project.getId(), snapshot, current)) outbox.insertIgnoringDuplicate(snapshot);
    }

    private UserNotification snapshot(String type, String key, Project project, User actor, User recipient, String title, OffsetDateTime at) {
        UserNotification snapshot = new UserNotification(); snapshot.setProjectId(project.getId());
        snapshot.setActorId(actor.getId()); snapshot.setActorName(actor.getName()); snapshot.setRecipientId(recipient.getId());
        snapshot.setType(type); snapshot.setEventKey(key); snapshot.setTaskTitle(title); snapshot.setCreatedAt(at);
        return snapshot;
    }
}
