package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.entity.UserNotification;
import com.blackbox.repository.EmailDeliveryLease;
import com.blackbox.repository.EmailDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.blackbox.repository.EmailDeliveryRepository.Completion.*;
import static com.blackbox.repository.EmailDeliveryRepository.Failure.*;

/** 짧은 독립 DB 구간 사이에서 SMTP를 호출한다. 인증 조회/transport가 없으면 아무 건도 획득하지 않는다. */
@Service
public class EmailDeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryWorker.class);
    private static final Duration MIN_REMAINING = Duration.ofSeconds(30);
    private final MailNotificationProperties settings;
    private final EmailDeliveryRepository deliveries;
    private final TaskRequestEmailEligibility tasks;
    private final ReviewRequestEmailEligibility reviews;
    private final ObjectProvider<EmailDeliveryAuthorization> authorizations;
    private final ObjectProvider<EmailNotificationTransport> transports;
    private final String frontendBaseUrl;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean unavailableReported = new AtomicBoolean();
    private final AtomicBoolean authenticationPaused = new AtomicBoolean();

    @Autowired
    public EmailDeliveryWorker(MailNotificationProperties settings, EmailDeliveryRepository deliveries,
            TaskRequestEmailEligibility tasks, ReviewRequestEmailEligibility reviews,
            ObjectProvider<EmailDeliveryAuthorization> authorizations,
            ObjectProvider<EmailNotificationTransport> transports,
            @Value("${app.frontend-base-url:}") String frontendBaseUrl) {
        this(settings, deliveries, tasks, reviews, authorizations, transports, frontendBaseUrl, Clock.systemUTC());
    }

    EmailDeliveryWorker(MailNotificationProperties settings, EmailDeliveryRepository deliveries,
            TaskRequestEmailEligibility tasks, ReviewRequestEmailEligibility reviews,
            ObjectProvider<EmailDeliveryAuthorization> authorizations,
            ObjectProvider<EmailNotificationTransport> transports, String frontendBaseUrl, Clock clock) {
        this.settings = settings; this.deliveries = deliveries; this.tasks = tasks; this.reviews = reviews;
        this.authorizations = authorizations; this.transports = transports;
        this.frontendBaseUrl = frontendBaseUrl; this.clock = clock;
    }

    @Scheduled(initialDelay = 15_000, fixedDelay = 10_000)
    public void poll() {
        if (authenticationPaused.get() || !enabled() || !running.compareAndSet(false, true)) return;
        UUID deliveryId = null;
        try {
            var authorization = authorizations.getIfAvailable();
            var transport = transports.getIfAvailable();
            if (authorization == null || transport == null) {
                if (unavailableReported.compareAndSet(false, true)) log.warn("EMAIL_DELIVERY_DEPENDENCY_UNAVAILABLE");
                return;
            }
            // 설정 오류로 대기를 소모하지 않도록 획득 전에 원문 링크 출처를 검사한다.
            var composer = new EmailNotificationComposer(URI.create(frontendBaseUrl));
            for (int count = 0; count < EmailDeliveryRepository.MAX_BATCH && enabled(); count++) {
                // 25건을 미리 잡지 않는다. 앞선 SMTP가 길어져도 뒤 건의 lease를 소모하지 않는다.
                deliveryId = null;
                var claimed = deliveries.claim(1, now());
                if (claimed.isEmpty()) return;
                deliveryId = claimed.getFirst().id();
                if (!process(claimed.getFirst(), authorization, transport, composer)) return;
            }
        } catch (DataAccessException failure) {
            logFailure(deliveryId, failure);
        } catch (RuntimeException failure) {
            // 원본 예외의 주소/본문/SMTP 응답을 로그에 싣지 않는다. 획득한 건은 lease 회수 대상이다.
            logFailure(deliveryId, failure);
        } finally {
            running.set(false);
        }
    }

    private boolean process(EmailDeliveryLease lease, EmailDeliveryAuthorization authorization,
            EmailNotificationTransport transport, EmailNotificationComposer composer) {
        if (!hasTime(lease)) return false;
        String recipient;
        EmailNotificationComposer.Content content;
        try {
            var first = authorization.current(lease.recipientId());
            if (!permitted(first, lease, null)) return skip(lease);
            var notification = snapshot(lease);
            Optional<String> current = switch (lease.eventType()) {
                case "TASK_COMPLETION_REQUESTED" -> tasks.currentRecipient(notification);
                case "REVIEW_REQUESTED" -> reviews.currentRecipient(notification);
                default -> Optional.empty();
            };
            if (current.isEmpty() || !permitted(first, lease, current.get())) return skip(lease);
            recipient = current.get();
            content = composer.compose(notification);
            // 권한 조회가 지연되는 동안 수신 설정/인증이 바뀌었을 수 있다.
            if (!enabled() || !permitted(authorization.current(lease.recipientId()), lease, recipient)) return skip(lease);
        } catch (DataAccessException failure) {
            logFailure(lease.id(), failure);
            deliveries.complete(lease, RETRY, TEMPORARY_FAILURE, now());
            return false;
        } catch (IllegalArgumentException failure) {
            deliveries.complete(lease, FAILED, PERMANENT_FAILURE, now());
            return true;
        }
        if (!hasTime(lease)) return false;
        EmailNotificationTransport.Result result;
        try {
            result = transport.send(lease.id(), recipient, content);
        } catch (RuntimeException failure) {
            logFailure(lease.id(), failure);
            result = EmailNotificationTransport.Result.UNKNOWN_OUTCOME;
        }
        if (result == null) result = EmailNotificationTransport.Result.UNKNOWN_OUTCOME;
        switch (result) {
            case ACCEPTED -> deliveries.complete(lease, SENT, null, now());
            case TEMPORARY_FAILURE, UNKNOWN_OUTCOME -> deliveries.complete(lease, RETRY,
                    EmailDeliveryRepository.Failure.valueOf(result.name()), now());
            case AUTHENTICATION_FAILURE -> {
                // 결과 저장이 실패하더라도 잘못된 계정으로 다음 회차에 재접속하지 않는다.
                if (authenticationPaused.compareAndSet(false, true))
                    log.warn("EMAIL_DELIVERY_AUTHENTICATION_PAUSED delivery={}", lease.id());
                deliveries.complete(lease, FAILED, AUTHENTICATION_FAILURE, now());
            }
            case PERMANENT_FAILURE -> deliveries.complete(lease, FAILED, PERMANENT_FAILURE, now());
        }
        // 계정/서버 쪽 실패는 현재 회차를 중단한다. 수신자별 영구 실패와 성공만 다음 건으로 간다.
        return result == EmailNotificationTransport.Result.ACCEPTED || result == EmailNotificationTransport.Result.PERMANENT_FAILURE;
    }

    private void logFailure(UUID deliveryId, RuntimeException failure) {
        log.warn("메일 발송 처리 실패 delivery={} 예외={}", deliveryId == null ? "-" : deliveryId,
                failure.getClass().getSimpleName());
    }

    private boolean permitted(Optional<EmailDeliveryAuthorization.State> result, EmailDeliveryLease lease, String recipient) {
        return result.filter(state -> state.permits(lease.createdAt(), recipient)).isPresent();
    }

    private boolean skip(EmailDeliveryLease lease) {
        deliveries.complete(lease, SKIPPED, CONDITION_CHANGED, now());
        return true;
    }

    private boolean hasTime(EmailDeliveryLease lease) {
        var current = now();
        return lease.claimedAt() != null && !lease.claimedAt().isAfter(current)
                && lease.claimedAt().plus(EmailDeliveryRepository.LEASE).isAfter(current.plus(MIN_REMAINING));
    }

    private boolean enabled() { return settings.isEnabled() && !settings.getRecipientAllowlist().isEmpty(); }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }

    /** 기존 조회 API/composer에 전달하는 값이며 저장하거나 재발행하지 않는다. */
    private UserNotification snapshot(EmailDeliveryLease lease) {
        var value = new UserNotification();
        value.setProjectId(lease.projectId()); value.setRecipientId(lease.recipientId());
        value.setActorId(lease.actorId()); value.setActorName(lease.actorName());
        value.setType(lease.eventType()); value.setEventKey(lease.eventKey());
        value.setTaskId(lease.taskId()); value.setDeliverableId(lease.deliverableId()); value.setReviewId(lease.reviewId());
        value.setTaskTitle(lease.targetTitle()); value.setCreatedAt(lease.createdAt());
        return value;
    }
}
