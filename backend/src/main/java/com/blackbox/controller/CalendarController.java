package com.blackbox.controller;

import com.blackbox.dto.*;
import com.blackbox.entity.User;
import com.blackbox.service.GoogleCalendarService;
import com.blackbox.service.CalendarOAuthStateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
public class CalendarController {

    private final GoogleCalendarService calendarService;
    private final CalendarOAuthStateService states;
    private static final String STATE_COOKIE = "calendarOAuthState";
    private static final String CALLBACK_PATH = "/api/auth/google/callback";

    public CalendarController(GoogleCalendarService calendarService, CalendarOAuthStateService states) {
        this.calendarService = calendarService;
        this.states = states;
    }

    // ── OAuth ─────────────────────────────────────────────────────────────

    /** Google OAuth 동의 화면 URL 반환 (인증 필요) */
    @GetMapping("/api/calendar/authorize")
    public ResponseEntity<GoogleAuthUrlResponse> authorize(@AuthenticationPrincipal User user, HttpServletRequest request) {
        String state = states.issue(user.getId());
        String url = calendarService.buildAuthorizationUrl(state);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, stateCookie(state, request,
                CalendarOAuthStateService.LIFETIME.getSeconds())).body(new GoogleAuthUrlResponse(url));
    }

    /** Google OAuth 콜백 — 코드 수신 → 토큰 저장 → 프로필 설정으로 리다이렉트 */
    @GetMapping("/api/auth/google/callback")
    public void callback(
            @RequestParam String code,
            @RequestParam String state,
            @CookieValue(name = STATE_COOKIE, required = false) String browserState,
            @AuthenticationPrincipal User user,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        UUID userId = states.consume(state, browserState, user == null ? null : user.getId());
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie("", request, 0));
        calendarService.handleCallback(code, userId);
        response.sendRedirect("/profile/settings?calendar=connected");
    }

    /** 구글 캘린더 연동 해제 */
    @DeleteMapping("/api/calendar/disconnect")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal User user) {
        calendarService.disconnect(user);
        return ResponseEntity.noContent().build();
    }

    // ── 연동 상태 조회 ────────────────────────────────────────────────────

    @GetMapping("/api/projects/{projectId}/calendar/status")
    public ResponseEntity<CalendarConnectionStatusResponse> status(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(calendarService.getConnectionStatus(projectId, user));
    }

    // ── 가용 시간 조회 ────────────────────────────────────────────────────

    /**
     * GET /api/projects/{projectId}/calendar/availability?date=YYYY-MM-DD
     * 팀원 전체 바쁜 시간 + 공통 빈 시간 반환
     */
    @GetMapping("/api/projects/{projectId}/calendar/availability")
    public ResponseEntity<CalendarAvailabilityResponse> availability(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "this_week") String date,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(calendarService.getAvailability(projectId, date, user));
    }

    // ── 캘린더 이벤트 등록 ────────────────────────────────────────────────

    /**
     * POST /api/projects/{projectId}/calendar/events
     * 확정된 회의를 팀원 전체 캘린더에 등록
     */
    @PostMapping("/api/projects/{projectId}/calendar/events")
    public ResponseEntity<Void> createEvent(
            @PathVariable UUID projectId,
            @RequestBody @Valid CalendarEventRequest req,
            @AuthenticationPrincipal User user) {
        calendarService.createCalendarEvents(projectId, req, user);
        return ResponseEntity.ok().build();
    }

    // ── 일정 조율 팀원 현황 (schedule 페이지용 alias) ─────────────────────

    @GetMapping("/api/projects/{projectId}/schedule/team-status")
    public ResponseEntity<CalendarConnectionStatusResponse> scheduleTeamStatus(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(calendarService.getConnectionStatus(projectId, user));
    }

    // ── AI 일정 추천 ──────────────────────────────────────────────────────

    /**
     * POST /api/calendar/recommend
     * 팀원 가용 시간 + 마감일 기반 최적 회의 시간 3개 추천
     */
    @PostMapping("/api/calendar/recommend")
    public ResponseEntity<CalendarRecommendResponse> recommend(
            @RequestBody @Valid CalendarRecommendRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(calendarService.recommendMeetingTimes(req, user));
    }

    private String stateCookie(String value, HttpServletRequest request, long maxAge) {
        boolean secure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        return ResponseCookie.from(STATE_COOKIE, value).httpOnly(true).secure(secure).sameSite("Lax")
                .path(CALLBACK_PATH).maxAge(maxAge).build().toString();
    }

}
