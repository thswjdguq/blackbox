package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.UserNotification;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.ProjectMemberRepository;
import com.blackbox.repository.ProjectRepository;
import com.blackbox.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;

/** 업무 결과 확인 요청만 담당한다. SMTP를 부르거나 입력 스냅숏을 저장하지 않는다. */
@Service
public class TaskRequestEmailEligibility {
    private final MailNotificationProperties settings;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final ProjectMemberRepository members;
    private final TaskWorkflowService workflow;
    private final TransactionTemplate reads;

    public TaskRequestEmailEligibility(MailNotificationProperties settings, ProjectRepository projects,
            UserRepository users, ProjectMemberRepository members, TaskWorkflowService workflow,
            PlatformTransactionManager transactions) {
        this.settings=settings; this.projects=projects; this.users=users; this.members=members; this.workflow=workflow;
        this.reads=new TransactionTemplate(transactions);
        this.reads.setReadOnly(true);
        this.reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 커밋된 업무·현재 권한을 읽고, 현재 계정 주소만 반환한다. 전송 중 철회/배달 회수까지 보장하지 않는다. */
    public Optional<String> currentRecipient(UserNotification request) {
        if (!settings.isEnabled() || settings.getRecipientAllowlist().isEmpty() || request == null
                || !"TASK_COMPLETION_REQUESTED".equals(request.getType()) || request.getProjectId() == null
                || request.getRecipientId() == null || request.getTaskId() == null || request.getActorId() == null
                || request.getDeliverableId() != null || request.getReviewId() != null) return Optional.empty();
        // view()의 REQUIRED가 NotFound/Forbidden으로 rollback-only를 남길 수 있으므로,
        // 예외는 읽기 트랜잭션이 정상적으로 롤백된 바깥에서 처리한다. DB 장애까지 숨기지 않는다.
        try {
            return reads.execute(status -> readCurrent(request));
        } catch (NotFoundException | ForbiddenException noLongerAvailable) {
            return Optional.empty();
        }
    }

    private Optional<String> readCurrent(UserNotification request) {
        var project=projects.findById(request.getProjectId()).orElse(null);
        var recipient=users.findById(request.getRecipientId()).orElse(null);
        if (project == null || recipient == null) return Optional.empty();
        var member=members.findByProjectAndUser(project, recipient).orElse(null);
        if (!new EmailNotificationPolicy(settings.isEnabled(), settings.getRecipientAllowlist())
                .permits(project.getId(), request, member)) return Optional.empty();
        var completion=workflow.view(project.getId(), request.getTaskId(), recipient);
        var task=completion.task();
        if (!completion.canReview() || !"IN_PROGRESS".equals(task.status()) || task.completionRequestedAt() == null
                || task.completionApprovedAt() != null || !Objects.equals(task.completionRequestedBy(), request.getActorId())
                || !Objects.equals(request.getEventKey(), "COMPLETION:" + task.id() + ":" + NotificationService.micros(task.completionRequestedAt()))) {
            return Optional.empty();
        }
        return Optional.of(recipient.getEmail().strip());
    }
}
