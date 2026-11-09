package com.blackbox.service;

import com.blackbox.exception.ForbiddenException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 사용자 ID를 OAuth state로 노출하지 않고, 연동을 시작한 브라우저의 일회성 요청만 받는다. */
@Service
public class CalendarOAuthStateService {
    public static final Duration LIFETIME = Duration.ofMinutes(10);
    private record Pending(UUID userId, Instant expiresAt) {}
    private final Map<String, Pending> pending = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public CalendarOAuthStateService() { this(Clock.systemUTC()); }
    CalendarOAuthStateService(Clock clock) { this.clock = clock; }

    public synchronized String issue(UUID userId) {
        Instant now = clock.instant();
        pending.values().removeIf(p -> !p.expiresAt().isAfter(now));
        if (pending.size() >= 10000) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "잠시 후 캘린더 연동을 다시 시작해 주세요.");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(state, new Pending(userId, now.plus(LIFETIME)));
        return state;
    }

    /** 검증과 삭제를 같은 잠금 안에서 수행해 동시에 온 콜백도 한 번만 외부 토큰 교환으로 보낸다. */
    public synchronized UUID consume(String state, String browserState, UUID signedInUserId) {
        if (state == null || state.length() != 43 || browserState == null || browserState.length() != 43
                || !MessageDigest.isEqual(state.getBytes(StandardCharsets.US_ASCII), browserState.getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
        Pending request = pending.get(state);
        if (request == null || !request.expiresAt().isAfter(clock.instant())) {
            pending.remove(state);
            throw invalid();
        }
        if (signedInUserId != null && !signedInUserId.equals(request.userId())) throw invalid();
        pending.remove(state);
        return request.userId();
    }

    private ForbiddenException invalid() {
        return new ForbiddenException("캘린더 연동 요청이 만료되었거나 유효하지 않습니다. 설정에서 다시 시작해 주세요.");
    }
}
