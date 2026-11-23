package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.UserNotification;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.ProjectMemberRepository;
import com.blackbox.repository.ProjectRepository;
import com.blackbox.repository.ReviewRoundRepository;
import com.blackbox.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;

/** A의 검토 읽기 계약과 업로더 판정을 소비한다. SMTP·저장·결정 명령은 호출하지 않는다. */
@Service
public class ReviewRequestEmailEligibility {
    private final MailNotificationProperties settings;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final ProjectMemberRepository members;
    private final ReviewRoundReader reviews;
    private final ReviewRoundRepository rounds;
    private final TransactionTemplate reads;

    public ReviewRequestEmailEligibility(MailNotificationProperties settings, ProjectRepository projects,
            UserRepository users, ProjectMemberRepository members, ReviewRoundReader reviews,
            ReviewRoundRepository rounds, PlatformTransactionManager transactions) {
        this.settings=settings; this.projects=projects; this.users=users; this.members=members;
        this.reviews=reviews; this.rounds=rounds;
        reads=new TransactionTemplate(transactions); reads.setReadOnly(true);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 현재 주소를 저장하지 않는다. 반환 뒤 별도로 SMTP를 호출할 worker를 위한 조회 경계다. */
    public Optional<String> currentRecipient(UserNotification request) {
        if (!settings.isEnabled() || settings.getRecipientAllowlist().isEmpty() || request == null
                || !"REVIEW_REQUESTED".equals(request.getType()) || request.getProjectId() == null
                || request.getActorId() == null || request.getRecipientId() == null || request.getTaskId() != null
                || request.getDeliverableId() == null || request.getReviewId() == null) return Optional.empty();
        try {
            return reads.execute(status -> readCurrent(request));
        } catch (NotFoundException | ForbiddenException unavailable) {
            // A의 REQUIRED가 롤백된 뒤 처리한다. 연결 장애 등은 발송 불가로 숨기지 않는다.
            return Optional.empty();
        }
    }

    private Optional<String> readCurrent(UserNotification request) {
        var project=projects.findById(request.getProjectId()).orElse(null);
        var recipient=users.findById(request.getRecipientId()).orElse(null);
        if (project == null || recipient == null) return Optional.empty();
        var current=members.findByProjectAndUser(project,recipient).orElse(null);
        if (!new EmailNotificationPolicy(settings.isEnabled(),settings.getRecipientAllowlist())
                .permits(project.getId(),request,current)) return Optional.empty();
        var view=reviews.get(project.getId(),request.getDeliverableId(),request.getReviewId());
        // 회차 id와 latest가 요청을 구분한다. 생산자의 key 문자열 형식을 여기서 다시 해석하지 않는다.
        if (!view.latest() || view.decision() != null || !"IN_REVIEW".equals(view.deliverableStatus())) return Optional.empty();
        var round=rounds.findById(view.reviewId()).orElseThrow(() -> new NotFoundException("검토 회차가 없습니다."));
        // 프로젝트/제출물/회차 관계는 reviews.get의 계약이 보장한다.
        if (!Objects.equals(round.getOpenedBy().getId(),request.getActorId())
                || ReviewRoundService.isRoundFileUploader(round,recipient)) return Optional.empty();
        return Optional.of(recipient.getEmail().strip());
    }
}
