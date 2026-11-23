package com.blackbox.service;

import com.blackbox.exception.CodedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 저장/인증은 대역이며 실제 DB·A 인증 API·SMTP를 사용하지 않는다. */
class EmailNotificationSettingsServiceTest {
    final UUID userId = UUID.randomUUID();
    final EmailNotificationPreferenceStore store = mock(EmailNotificationPreferenceStore.class);
    final EmailNotificationVerificationReader reader = mock(EmailNotificationVerificationReader.class);
    final AtomicReference<EmailNotificationPreferenceStore.Preference> saved = new AtomicReference<>();
    final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-11-23T06:30:00Z"));
    final Clock clock = mock(Clock.class);
    EmailNotificationSettingsService service;

    @BeforeEach void prepare() {
        when(clock.getZone()).thenReturn(ZoneOffset.UTC); when(clock.instant()).thenAnswer(call -> time.get());
        service = service(store, reader);
        when(reader.current(userId)).thenReturn(Optional.of(status(null, true, null)));
        when(store.find(userId)).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(store.update(eq(userId), anyBoolean(), anyString(), any())).thenAnswer(call -> {
            var previous = saved.get() == null ? EmailNotificationPreferenceStore.Preference.off() : saved.get();
            var next = previous.transition(call.getArgument(1), call.getArgument(2), call.getArgument(3));
            saved.set(next); return next;
        });
    }

    @Test void defaultGetIsOffAndDoesNotCreateASettingOrRequestVerification() {
        var result = service.get(userId);
        assertFalse(result.enabled()); assertFalse(result.emailVerified()); assertFalse(result.verificationRequired());
        assertTrue(result.verificationAvailable()); assertNull(result.updatedAt());
        verify(store, never()).update(any(), anyBoolean(), anyString(), any());
    }

    @Test void enablingAnUnverifiedAddressImmediatelyRequiresVerification() {
        var result = service.update(userId, true);
        assertTrue(result.enabled()); assertFalse(result.emailVerified()); assertTrue(result.verificationRequired());
        assertEquals(EmailNotificationSettingsService.NOTICE_VERSION, saved.get().noticeVersion());
        verify(store).update(eq(userId), eq(true), eq(EmailNotificationSettingsService.NOTICE_VERSION), any());
    }

    @Test void currentVerificationIsReadAgainWithoutChangingThePreferenceTime() {
        var before = service.update(userId, true).updatedAt();
        var verifiedAt = OffsetDateTime.now(clock);
        when(reader.current(userId)).thenReturn(Optional.of(status(verifiedAt, true, null)));
        var result = service.get(userId);
        assertTrue(result.emailVerified()); assertFalse(result.verificationRequired()); assertEquals(before, result.updatedAt());
    }

    @Test void verificationAvailabilityUsesTheProducerReasonVerbatim() {
        when(reader.current(userId)).thenReturn(Optional.of(status(null, false, "합성 서버의 확인 요청 안내")));
        var result = service.update(userId, true);
        assertFalse(result.verificationAvailable()); assertTrue(result.verificationRequired());
        assertEquals("합성 서버의 확인 요청 안내", result.verificationUnavailableReason());
    }

    @Test void repeatedPutKeepsConsentTimeAndReactivationMovesIt() {
        var first = service.update(userId, true).updatedAt(); time.set(time.get().plusSeconds(60));
        assertEquals(first, service.update(userId, true).updatedAt());
        assertFalse(service.update(userId, false).enabled()); time.set(time.get().plusSeconds(60));
        var next = service.update(userId, true);
        assertTrue(next.updatedAt().isAfter(first)); assertEquals(next.updatedAt(), saved.get().enabledAt());
    }

    @Test void missingStoreOrVerificationReaderIsUnavailableWithoutPretendingToSave() {
        for (var missing : new EmailNotificationSettingsService[]{service(null, reader), service(store, null)}) {
            var error = assertThrows(CodedException.class, () -> missing.update(userId, true));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus()); assertEquals("EMAIL_SETTINGS_UNAVAILABLE", error.getCode());
        }
        verifyNoInteractions(store, reader);
    }

    @Test void unknownCurrentAccountIsUnauthorizedAndNeverSaved() {
        when(reader.current(userId)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> service.update(userId, true));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode()); verifyNoInteractions(store);
    }

    @Test void verificationReadFailureIsNotTurnedIntoFalseOrASuccessfulSave() {
        var failure = new DataAccessResourceFailureException("synthetic-verification-failure");
        when(reader.current(userId)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> service.update(userId, true)));
        verifyNoInteractions(store);
    }

    @Test void preferenceDatabaseFailureIsNotReturnedAsASuccessfulDefault() {
        var failure = new DataAccessResourceFailureException("synthetic-preference-failure");
        when(store.find(userId)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> service.get(userId)));
    }

    @Test void incompleteAvailabilityCannotClaimTheFeatureIsUsable() {
        when(reader.current(userId)).thenReturn(Optional.of(status(null, false, " ")));
        assertThrows(IllegalStateException.class, () -> service.update(userId, true)); verifyNoInteractions(store);
        assertFalse(status(null, true, null).toString().contains("example.test"));
    }

    private EmailNotificationSettingsService service(EmailNotificationPreferenceStore value, EmailNotificationVerificationReader verification) {
        return new EmailNotificationSettingsService(
                new StaticListableBeanFactory(value == null ? Map.of() : Map.of("store", value)).getBeanProvider(EmailNotificationPreferenceStore.class),
                new StaticListableBeanFactory(verification == null ? Map.of() : Map.of("reader", verification)).getBeanProvider(EmailNotificationVerificationReader.class), clock);
    }

    private EmailNotificationVerificationReader.Status status(OffsetDateTime verifiedAt, boolean available, String reason) {
        return new EmailNotificationVerificationReader.Status("synthetic@example.test", verifiedAt, available, reason);
    }
}
