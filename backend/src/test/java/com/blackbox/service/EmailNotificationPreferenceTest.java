package com.blackbox.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static com.blackbox.service.EmailNotificationPreferenceStore.Preference;
import static org.junit.jupiter.api.Assertions.*;

/** DB 동시성 대신 수신 설정의 보존/재활성화 시각 규칙만 검사한다. */
class EmailNotificationPreferenceTest {
    final OffsetDateTime first = OffsetDateTime.parse("2026-11-23T15:30:00.123456789+09:00");

    @Test void absentPreferenceIsOffWithoutInventedConsentOrTime() {
        var value = Preference.off();
        assertFalse(value.enabled()); assertNull(value.noticeVersion()); assertNull(value.enabledAt());
        assertNull(value.disabledAt()); assertNull(value.updatedAt());
        assertSame(value, value.transition(false, "v1", first));
    }

    @Test void enablingRecordsTheNoticeAndUtcDatabasePrecision() {
        var value = Preference.off().transition(true, "v1", first);
        assertTrue(value.enabled()); assertEquals("v1", value.noticeVersion());
        assertEquals(OffsetDateTime.parse("2026-11-23T06:30:00.123456Z"), value.enabledAt());
        assertEquals(value.enabledAt(), value.updatedAt()); assertNull(value.disabledAt());
    }

    @Test void repeatingEnabledDoesNotRenewConsentOrItsTime() {
        var value = Preference.off().transition(true, "v1", first);
        assertSame(value, value.transition(true, "v2", first.plusDays(1)));
    }

    @Test void optingOutPreservesTheEarlierConsentForAudit() {
        var enabled = Preference.off().transition(true, "v1", first);
        var disabled = enabled.transition(false, "v2", first.plusHours(1));
        assertFalse(disabled.enabled()); assertEquals(enabled.enabledAt(), disabled.enabledAt());
        assertEquals("v1", disabled.noticeVersion()); assertEquals(disabled.disabledAt(), disabled.updatedAt());
    }

    @Test void repeatedOptOutDoesNotChangeItsTime() {
        var disabled = Preference.off().transition(true, "v1", first).transition(false, "v1", first.plusHours(1));
        assertSame(disabled, disabled.transition(false, "v1", first.plusDays(1)));
    }

    @Test void reenablingMovesTheConsentCutoffPastOlderPendingMail() {
        var previous = Preference.off().transition(true, "v1", first);
        var oldEvent = first.plusMinutes(1);
        var next = previous.transition(false, "v1", first.plusMinutes(2)).transition(true, "v2", first.plusMinutes(3));
        assertTrue(next.enabledAt().isAfter(oldEvent)); assertEquals("v2", next.noticeVersion());
        assertNotNull(next.disabledAt());
    }

    @Test void missingConsentVersionCannotEnableMail() {
        for (String version : new String[]{null, "", " "})
            assertThrows(IllegalArgumentException.class, () -> Preference.off().transition(true, version, first));
    }
}
