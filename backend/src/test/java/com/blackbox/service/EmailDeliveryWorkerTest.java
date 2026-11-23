package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.repository.EmailDeliveryLease;
import com.blackbox.repository.EmailDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.blackbox.repository.EmailDeliveryRepository.Completion.*;
import static com.blackbox.repository.EmailDeliveryRepository.Failure.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 실제 주소·SMTP를 사용하지 않고 발송 경계와 인증 필수 조건을 확인한다. */
class EmailDeliveryWorkerTest {
    final OffsetDateTime now = OffsetDateTime.parse("2026-11-23T03:00:00Z");
    final MailNotificationProperties settings = new MailNotificationProperties();
    final EmailDeliveryRepository deliveries = mock(EmailDeliveryRepository.class);
    final TaskRequestEmailEligibility tasks = mock(TaskRequestEmailEligibility.class);
    final ReviewRequestEmailEligibility reviews = mock(ReviewRequestEmailEligibility.class);
    final EmailDeliveryAuthorization authorization = mock(EmailDeliveryAuthorization.class);
    final EmailNotificationTransport transport = mock(EmailNotificationTransport.class);
    @SuppressWarnings("unchecked") final ObjectProvider<EmailDeliveryAuthorization> authProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked") final ObjectProvider<EmailNotificationTransport> transportProvider = mock(ObjectProvider.class);
    final Clock clock = Clock.fixed(now.toInstant(), ZoneOffset.UTC);
    EmailDeliveryWorker worker;
    EmailDeliveryLease lease;
    final String address = "synthetic@example.test";

    @BeforeEach void prepare() {
        settings.setEnabled(true); settings.setRecipientAllowlist(List.of(address));
        when(authProvider.getIfAvailable()).thenReturn(authorization);
        when(transportProvider.getIfAvailable()).thenReturn(transport);
        worker = new EmailDeliveryWorker(settings, deliveries, tasks, reviews, authProvider, transportProvider,
                "http://localhost:13000", clock);
        lease = lease("TASK_COMPLETION_REQUESTED", now);
        when(deliveries.claim(eq(1), any())).thenReturn(List.of(lease), List.of());
        when(authorization.current(any())).thenReturn(Optional.of(verified()));
        when(tasks.currentRecipient(any())).thenReturn(Optional.of(address));
        when(reviews.currentRecipient(any())).thenReturn(Optional.of(address));
        when(transport.send(any(), any(), any())).thenReturn(EmailNotificationTransport.Result.ACCEPTED);
    }

    @Test void disabledEmptyAllowlistOrMissingDependenciesNeverClaimAnything() {
        settings.setEnabled(false); worker.poll();
        settings.setEnabled(true); settings.setRecipientAllowlist(List.of()); worker.poll();
        settings.setRecipientAllowlist(List.of(address)); when(authProvider.getIfAvailable()).thenReturn(null); worker.poll();
        when(authProvider.getIfAvailable()).thenReturn(authorization); when(transportProvider.getIfAvailable()).thenReturn(null); worker.poll();
        verifyNoInteractions(deliveries, tasks, reviews, authorization, transport);
    }

    @Test void unverifiedOptedOutAndUnknownUsersNeverReachTheTransport() {
        for (Optional<EmailDeliveryAuthorization.State> state : List.of(Optional.<EmailDeliveryAuthorization.State>empty(),
                Optional.of(new EmailDeliveryAuthorization.State(address, true, now.minusDays(1), null)),
                Optional.of(new EmailDeliveryAuthorization.State(address, false, now.minusDays(1), now.minusDays(1))))) {
            when(deliveries.claim(eq(1), any())).thenReturn(List.of(lease), List.of());
            when(authorization.current(any())).thenReturn(state);
            worker.poll();
        }
        verifyNoInteractions(tasks, reviews, transport);
        verify(deliveries, times(3)).complete(lease, SKIPPED, CONDITION_CHANGED, now);
    }

    @Test void verificationOrReenablingAfterTheEventDoesNotReviveOldMail() {
        for (var state : List.of(new EmailDeliveryAuthorization.State(address, true, now.plusSeconds(1), now.minusDays(1)),
                new EmailDeliveryAuthorization.State(address, true, now.minusDays(1), now.plusSeconds(1)))) {
            when(deliveries.claim(eq(1), any())).thenReturn(List.of(lease), List.of());
            when(authorization.current(any())).thenReturn(Optional.of(state)); worker.poll();
        }
        verifyNoInteractions(tasks, transport);
        verify(deliveries, times(2)).complete(lease, SKIPPED, CONDITION_CHANGED, now);
    }

    @Test void currentRequestAndCurrentAddressMustStillMatch() {
        when(tasks.currentRecipient(any())).thenReturn(Optional.empty()); worker.poll();
        when(deliveries.claim(eq(1), any())).thenReturn(List.of(lease), List.of());
        when(tasks.currentRecipient(any())).thenReturn(Optional.of("changed@example.test")); worker.poll();
        verifyNoInteractions(transport);
        verify(deliveries, times(2)).complete(lease, SKIPPED, CONDITION_CHANGED, now);
    }

    @Test void consentIsRecheckedAfterTheCurrentRequestLookup() {
        when(authorization.current(any())).thenReturn(Optional.of(verified()), Optional.empty());
        worker.poll();
        verify(tasks).currentRecipient(any()); verifyNoInteractions(transport);
        verify(deliveries).complete(lease, SKIPPED, CONDITION_CHANGED, now);
    }

    @Test void thirtySecondsOrLessRemainingNeverStartsSmtp() {
        lease = lease("TASK_COMPLETION_REQUESTED", now.minusSeconds(90));
        when(deliveries.claim(eq(1), any())).thenReturn(List.of(lease)); worker.poll();
        verifyNoInteractions(authorization, tasks, transport);
        verify(deliveries, never()).complete(any(), any(), any(), any());
        verify(deliveries).claim(1, now);
    }

    @Test void timeSpentReadingTheRequestCannotConsumeTheSmtpReserve() {
        final Instant[] time = {now.toInstant()};
        Clock advancing = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return time[0]; }
        };
        worker = new EmailDeliveryWorker(settings, deliveries, tasks, reviews, authProvider, transportProvider,
                "http://localhost:13000", advancing);
        when(tasks.currentRecipient(any())).thenAnswer(call -> { time[0] = now.plusSeconds(90).toInstant(); return Optional.of(address); });
        worker.poll(); verifyNoInteractions(transport);
        verify(deliveries, never()).complete(any(), any(), any(), any());
    }

    @ParameterizedTest @EnumSource(EmailNotificationTransport.Result.class)
    void transportResultsUseTheExistingRetryAndFencingRepository(EmailNotificationTransport.Result result) {
        when(transport.send(any(), any(), any())).thenReturn(result); worker.poll();
        var completion = switch (result) {
            case ACCEPTED -> SENT;
            case TEMPORARY_FAILURE, UNKNOWN_OUTCOME -> RETRY;
            case AUTHENTICATION_FAILURE, PERMANENT_FAILURE -> FAILED;
        };
        var failure = result == EmailNotificationTransport.Result.ACCEPTED ? null
                : EmailDeliveryRepository.Failure.valueOf(result.name());
        verify(deliveries).complete(lease, completion, failure, now);
        verify(transport).send(eq(lease.id()), eq(address), argThat(content -> content.target().getHost().equals("localhost")
                && content.target().getPath().equals("/projects/" + lease.projectId() + "/board")
                && content.target().getQuery().equals("task=" + lease.taskId())));
    }

    @Test void reviewRequestsUseTheReviewGuardAndTheExistingDeliverableReviewTab() {
        lease = lease("REVIEW_REQUESTED", now); when(deliveries.claim(eq(1), any())).thenReturn(List.of(lease), List.of());
        worker.poll(); verifyNoInteractions(tasks); verify(reviews).currentRecipient(any());
        verify(transport).send(eq(lease.id()), eq(address), argThat(content -> content.target().getPath().equals(
                "/projects/" + lease.projectId() + "/deliverables/" + lease.deliverableId())
                && content.target().getQuery().equals("tab=review")));
        verify(deliveries).complete(lease, SENT, null, now);
    }

    @Test void databaseReadFailureIsRetriedInsteadOfPretendingTheUserIsIneligible() {
        when(authorization.current(any())).thenThrow(new DataAccessResourceFailureException("synthetic"));
        worker.poll(); verifyNoInteractions(transport);
        verify(deliveries).complete(lease, RETRY, TEMPORARY_FAILURE, now);
    }

    @Test void anUnclassifiedTransportFailureIsUnknownAndNeverSent() {
        when(transport.send(any(), any(), any())).thenThrow(new IllegalStateException("synthetic"));
        worker.poll(); verify(deliveries).complete(lease, RETRY, UNKNOWN_OUTCOME, now);
        verify(deliveries, never()).complete(any(), eq(SENT), any(), any());
    }

    @Test void reentryCannotClaimAnotherBatchWhileSmtpIsRunning() {
        when(transport.send(any(), any(), any())).thenAnswer(call -> { worker.poll(); return EmailNotificationTransport.Result.ACCEPTED; });
        worker.poll(); verify(transport, times(1)).send(any(), any(), any());
        verify(deliveries, times(2)).claim(1, now);
    }

    @Test void aPollClaimsAtMostTwentyFiveOneAtATime() {
        when(deliveries.claim(eq(1), any())).thenAnswer(call -> List.of(lease("TASK_COMPLETION_REQUESTED", now)));
        worker.poll(); verify(deliveries, times(25)).claim(1, now);
        verify(transport, times(25)).send(any(), eq(address), any());
    }

    @Test void invalidFrontendOriginIsRejectedBeforeClaiming() {
        worker = new EmailDeliveryWorker(settings, deliveries, tasks, reviews, authProvider, transportProvider,
                "http://untrusted.example", clock); worker.poll(); verifyNoInteractions(deliveries, transport);
        assertFalse(verified().toString().contains(address));
    }

    private EmailDeliveryAuthorization.State verified() {
        return new EmailDeliveryAuthorization.State(address, true, now.minusDays(1), now.minusDays(1));
    }

    private EmailDeliveryLease lease(String type, OffsetDateTime claimedAt) {
        boolean review = "REVIEW_REQUESTED".equals(type);
        return new EmailDeliveryLease(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "합성 요청자", type, "synthetic-event", review ? null : UUID.randomUUID(),
                review ? UUID.randomUUID() : null, review ? UUID.randomUUID() : null, "합성 결과",
                now, claimedAt, 1);
    }
}
