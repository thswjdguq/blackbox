package com.blackbox.service;

import com.blackbox.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CalendarOAuthStateServiceTest {
    final UUID owner = UUID.randomUUID();
    final MutableClock clock = new MutableClock();
    final CalendarOAuthStateService states = new CalendarOAuthStateService(clock);

    @Test void opaqueBrowserBoundStateWorksOnceWithoutAnAccessToken() {
        String state = states.issue(owner);
        assertEquals(43, state.length()); assertFalse(state.contains(owner.toString()));
        assertEquals(owner, states.consume(state, state, null));
        assertThrows(ForbiddenException.class, () -> states.consume(state, state, owner));
    }
    @Test void missingWrongOrForgedBrowserStateNeverConsumesTheRequest() {
        String state = states.issue(owner);
        assertThrows(ForbiddenException.class, () -> states.consume(state, null, owner));
        assertThrows(ForbiddenException.class, () -> states.consume(state, states.issue(owner), owner));
        assertThrows(ForbiddenException.class, () -> states.consume(owner.toString(), owner.toString(), owner));
        assertThrows(ForbiddenException.class, () -> states.consume("x".repeat(43), "x".repeat(43), owner));
        assertEquals(owner, states.consume(state, state, owner));
    }
    @Test void switchingAccountsCannotLinkThePreviousAccount() {
        String state = states.issue(owner);
        assertThrows(ForbiddenException.class, () -> states.consume(state, state, UUID.randomUUID()));
        assertEquals(owner, states.consume(state, state, owner));
    }
    @Test void expiresAtTenMinutesAndDoesNotSurviveServerRestart() {
        String state = states.issue(owner);
        assertThrows(ForbiddenException.class, () -> new CalendarOAuthStateService(clock).consume(state, state, owner));
        clock.now = clock.now.plus(CalendarOAuthStateService.LIFETIME);
        assertThrows(ForbiddenException.class, () -> states.consume(state, state, owner));
    }
    @Test void concurrentCallbacksHaveExactlyOneWinner() throws Exception {
        String state = states.issue(owner);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<Boolean> callback = () -> { start.await(); try { states.consume(state, state, owner); return true; }
                catch (ForbiddenException e) { return false; } };
            Future<Boolean> one = pool.submit(callback), two = pool.submit(callback); start.countDown();
            assertNotEquals(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-09T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
