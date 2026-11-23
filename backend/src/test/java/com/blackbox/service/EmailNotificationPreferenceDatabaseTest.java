package com.blackbox.service;

import com.blackbox.entity.User;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.blackbox.repository.ReviewFixtures.user;
import static org.junit.jupiter.api.Assertions.*;

/** 실제 V40 저장·트리거·동시 요청 검사. 합성 계정만 사용하며 SMTP는 켜지 않는다. */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0")
@ActiveProfiles("test")
class EmailNotificationPreferenceDatabaseTest {
    @Autowired EmailNotificationPreferenceStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ScoreScheduler scheduler;

    @Test void missingPreferenceAndFirstOptOutDoNotCreateConsent() {
        UUID id = account().getId();
        assertTrue(store.find(id).isEmpty());
        assertEquals(EmailNotificationPreferenceStore.Preference.off(), store.update(id, false, "v1", now()));
        assertEquals(0, count(id));
    }

    @Test void firstOptInPersistsConsentButDoesNotVerifyTheAddress() {
        UUID id = account().getId(); var at = now();
        var saved = store.update(id, true, "v1", at);
        assertTrue(saved.enabled()); assertEquals("v1", saved.noticeVersion());
        assertEquals(at.toInstant(), saved.enabledAt().toInstant());
        assertEquals(saved, store.find(id).orElseThrow());
        assertNull(jdbc.queryForObject("SELECT email_verified_at FROM users WHERE id=?", OffsetDateTime.class, id));
    }

    @Test void identicalPutDoesNotFireUpdatedAtTriggerOrRenewConsent() {
        UUID id = account().getId(); var saved = store.update(id, true, "v1", now());
        assertEquals(saved, store.update(id, true, "v2", now().plusDays(1)));
        assertEquals(saved, store.find(id).orElseThrow());
        var off = store.update(id, false, "v2", now());
        assertEquals(off, store.update(id, false, "v3", now().plusDays(2)));
        assertEquals(off, store.find(id).orElseThrow());
    }

    @Test void changedPutReturnsDatabaseTriggerTimeRatherThanJavaTime() {
        UUID id = account().getId(); store.update(id, true, "v1", now());
        var artificial = OffsetDateTime.parse("2020-01-01T00:00:00Z");
        var off = store.update(id, false, "v1", artificial);
        assertEquals(artificial, off.disabledAt());
        assertNotEquals(artificial, off.updatedAt());
        assertEquals(off, store.find(id).orElseThrow());
    }

    @Test void reenablingAdvancesTheCutoffAndKeepsEarlierOptOut() {
        UUID id = account().getId(); var first = now();
        store.update(id, true, "v1", first);
        var disabled = store.update(id, false, "v1", first.plusSeconds(1));
        var enabled = store.update(id, true, "v2", first.plusSeconds(2));
        assertTrue(enabled.enabledAt().isAfter(first)); assertEquals("v2", enabled.noticeVersion());
        assertEquals(disabled.disabledAt(), enabled.disabledAt());
        assertEquals(enabled, store.find(id).orElseThrow());
    }

    @Test void rollbackDoesNotPersistAnOptIn() {
        UUID id = account().getId();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            store.update(id, true, "v1", now()); status.setRollbackOnly();
        });
        assertEquals(0, count(id));
    }

    @Test void concurrentFirstOptInsCreateOneRowAndKeepTheFirstConsent() throws Exception {
        UUID id = account().getId(); var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return store.update(id, true, "v1", now()); });
            var second = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return store.update(id, true, "v2", now().plusSeconds(1)); });
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, count(id));
        } finally { pool.shutdownNow(); }
    }

    @Test void missingAccountCannotCreateAnOrphanPreference() {
        UUID id = UUID.randomUUID();
        var error = assertThrows(ResponseStatusException.class, () -> store.update(id, true, "v1", now()));
        assertEquals(401, error.getStatusCode().value()); assertEquals(0, count(id));
    }

    private User account() {
        return new TransactionTemplate(transactions).execute(status -> {
            var value = user(em, "합성 수신 설정 저장"); em.flush(); return value;
        });
    }
    private int count(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM email_notification_preferences WHERE user_id=?", Integer.class, id);
    }
    private OffsetDateTime now() { return OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
}
