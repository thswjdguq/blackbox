package com.blackbox.service;

import com.blackbox.dto.*;
import com.blackbox.entity.GoogleCalendarToken;
import com.blackbox.entity.User;
import com.blackbox.repository.GoogleCalendarTokenRepository;
import com.blackbox.repository.ProjectMemberRepository;
import com.blackbox.repository.ProjectRepository;
import com.blackbox.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

    private static final String GOOGLE_AUTH_URL   = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL  = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_CAL_URL    = "https://www.googleapis.com/calendar/v3";
    private static final String SCOPE =
            "https://www.googleapis.com/auth/calendar.events " +
            "https://www.googleapis.com/auth/calendar.readonly";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    private final GoogleCalendarTokenRepository tokenRepo;
    private final UserRepository                userRepo;
    private final ProjectRepository             projectRepo;
    private final ProjectMemberRepository       memberRepo;
    private final ClaudeService                 claudeService;
    private final OpenAiService                 openAiService;

    private final WebClient webClient = WebClient.builder().build();

    public GoogleCalendarService(
            @Value("${external.google.client-id:}") String clientId,
            @Value("${external.google.client-secret:}") String clientSecret,
            @Value("${external.google.redirect-uri:http://localhost/api/auth/google/callback}") String redirectUri,
            GoogleCalendarTokenRepository tokenRepo,
            UserRepository userRepo,
            ProjectRepository projectRepo,
            ProjectMemberRepository memberRepo,
            ClaudeService claudeService,
            OpenAiService openAiService) {
        this.clientId     = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri  = redirectUri;
        this.tokenRepo    = tokenRepo;
        this.userRepo     = userRepo;
        this.projectRepo  = projectRepo;
        this.memberRepo   = memberRepo;
        this.claudeService  = claudeService;
        this.openAiService  = openAiService;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    // ── OAuth ─────────────────────────────────────────────────────────────

    public String buildAuthorizationUrl(UUID userId) {
        return UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", userId.toString())
                .build().toUriString();
    }

    @Transactional
    public void handleCallback(String code, String stateUserId) {
        UUID userId = UUID.fromString(stateUserId);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Map<?, ?> tokenResponse = exchangeCodeForTokens(code);

        GoogleCalendarToken token = tokenRepo.findByUser(user)
                .orElseGet(() -> { GoogleCalendarToken t = new GoogleCalendarToken(); t.setUser(user); return t; });

        token.setAccessToken((String) tokenResponse.get("access_token"));
        String refreshToken = (String) tokenResponse.get("refresh_token");
        if (refreshToken != null) token.setRefreshToken(refreshToken);

        Number expiresIn = (Number) tokenResponse.get("expires_in");
        if (expiresIn != null) {
            token.setTokenExpiry(OffsetDateTime.now().plusSeconds(expiresIn.longValue()));
        }
        tokenRepo.save(token);
    }

    // ── 가용 시간 조회 ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CalendarAvailabilityResponse getAvailability(UUID projectId, String date, User currentUser) {
        var project = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        List<User> members = memberRepo.findByProject(project).stream()
                .map(pm -> pm.getUser())
                .collect(Collectors.toList());

        LocalDate targetDate = parseDate(date);
        String timeMin = targetDate.atStartOfDay(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String timeMax = targetDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        List<CalendarAvailabilityResponse.BusySlot> allBusy = new ArrayList<>();
        List<CalendarAvailabilityResponse.MemberStatus> memberStatuses = new ArrayList<>();

        for (User member : members) {
            Optional<GoogleCalendarToken> tokenOpt = tokenRepo.findByUser(member);
            boolean connected = tokenOpt.isPresent();
            memberStatuses.add(new CalendarAvailabilityResponse.MemberStatus(
                    member.getId().toString(), member.getName(), connected));

            if (connected) {
                GoogleCalendarToken tok = tokenOpt.get();
                String accessToken = ensureFreshToken(tok);
                List<CalendarAvailabilityResponse.BusySlot> busy = fetchBusySlots(accessToken, timeMin, timeMax, member.getName());
                allBusy.addAll(busy);
            }
        }

        List<CalendarAvailabilityResponse.FreeSlot> freeSlots = computeFreeSlots(allBusy, targetDate);
        return new CalendarAvailabilityResponse(allBusy, freeSlots, memberStatuses);
    }

    // ── 캘린더 이벤트 등록 ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void createCalendarEvents(CalendarEventRequest req, User currentUser) {
        List<UUID> attendeeIds = req.attendeeIds() != null ? req.attendeeIds() : List.of();
        List<User> attendees = attendeeIds.isEmpty()
                ? List.of(currentUser)
                : userRepo.findAllById(attendeeIds);

        for (User attendee : attendees) {
            tokenRepo.findByUser(attendee).ifPresent(tok -> {
                try {
                    String accessToken = ensureFreshToken(tok);
                    createGoogleEvent(accessToken, req, attendees);
                } catch (Exception ignored) {
                    // 토큰 갱신 실패 시 해당 참석자 캘린더 등록 스킵
                }
            });
        }
    }

    // ── 점수제 추천 내부 타입 ─────────────────────────────────────────────

    /** Google Calendar 이벤트 (HARD: 시간 일정 / SOFT: 종일 일정) */
    private record CalendarEventInfo(String memberName, boolean allDay,
                                     ZonedDateTime start, ZonedDateTime end) {}

    /** 점수가 매겨진 후보 슬롯 */
    private record ScoredSlot(ZonedDateTime start, int score, List<String> softBlockMembers) {}

    // ── AI 일정 추천 (점수제) ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CalendarRecommendResponse recommendMeetingTimes(CalendarRecommendRequest req, User currentUser) {
        var project = projectRepo.findById(req.projectId())
                .orElseThrow(() -> new RuntimeException("Project not found"));

        List<User> targetMembers = req.attendeeIds() != null && !req.attendeeIds().isEmpty()
                ? userRepo.findAllById(req.attendeeIds())
                : memberRepo.findByProject(project).stream().map(pm -> pm.getUser()).collect(Collectors.toList());

        String targetDate = resolveTargetDate(req.targetDate());
        LocalDate startDate = parseDate(targetDate);
        ZoneId kst = ZoneId.of("Asia/Seoul");
        // UTC(Z) 형식 사용 — +09:00의 '+' 가 URL 쿼리에서 공백으로 해석되는 문제 방지
        String timeMin = startDate.atStartOfDay(kst).toInstant().toString();
        String timeMax = startDate.plusDays(7).atStartOfDay(kst).toInstant().toString();

        // ── 1. 연동된 팀원 이벤트 수집 (allDay 여부 포함) ───────────────────────
        List<CalendarEventInfo> allEvents = new ArrayList<>();
        for (User m : targetMembers) {
            tokenRepo.findByUser(m).ifPresent(tok -> {
                try {
                    String at = ensureFreshToken(tok);
                    allEvents.addAll(fetchCalendarEventsWithTypes(at, timeMin, timeMax, m.getName()));
                } catch (Exception ignored) {
                    // 해당 멤버 스킵 — 일정 없음으로 처리
                }
            });
        }

        // ── 2. 슬롯 점수 계산 ────────────────────────────────────────────────
        List<ScoredSlot> scoredSlots = computeScoredSlots(allEvents, startDate, 7);

        if (scoredSlots.isEmpty()) {
            return new CalendarRecommendResponse(List.of(),
                    "이 기간에는 시간이 명시된 일정이 전원 겹쳐서 가능한 시간이 없습니다. 다른 기간을 선택해 주세요.");
        }

        // ── 3. 상위 3개 선택 + AI로 추천 이유 생성 ──────────────────────────
        List<ScoredSlot> top3 = scoredSlots.subList(0, Math.min(3, scoredSlots.size()));
        List<String> memberNames = targetMembers.stream().map(User::getName).collect(Collectors.toList());
        String prompt = buildScoredSlotPrompt(memberNames, top3, req.projectDeadline());

        String[] reasons = new String[0];
        try {
            String raw = "";
            if (claudeService.isConfigured()) {
                raw = claudeService.rawCall(prompt, 500);
            } else if (openAiService.isConfigured()) {
                raw = openAiService.rawCall(prompt, 500);
            } else {
                throw new IllegalStateException("AI API 키가 설정되지 않았습니다 (CLAUDE_API_KEY 또는 OPENAI_API_KEY 필요)");
            }
            reasons = parseReasonArray(raw);
        } catch (IllegalStateException e) {
            throw e; // AI 미설정은 그대로 전파
        } catch (Exception ignored) {
            // AI 호출 실패 → 기본 이유로 대체
        }

        // ── 4. 응답 구성 ────────────────────────────────────────────────────
        DateTimeFormatter iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        List<CalendarRecommendResponse.Recommendation> recs = new ArrayList<>();
        for (int i = 0; i < top3.size(); i++) {
            ScoredSlot slot = top3.get(i);
            String reason = (i < reasons.length && !reasons[i].isBlank())
                    ? reasons[i] : defaultReason(slot);
            recs.add(new CalendarRecommendResponse.Recommendation(
                    slot.start().format(iso), 60, reason, i + 1,
                    slot.score(), slot.softBlockMembers(), !slot.softBlockMembers().isEmpty()));
        }
        return new CalendarRecommendResponse(recs);
    }

    // ── 연동 상태 조회 ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CalendarConnectionStatusResponse getConnectionStatus(UUID projectId, User currentUser) {
        boolean myConnected = tokenRepo.existsByUser(currentUser);

        var project = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        List<CalendarConnectionStatusResponse.MemberCalendarStatus> statuses =
                memberRepo.findByProject(project).stream().map(pm -> {
                    User u = pm.getUser();
                    return new CalendarConnectionStatusResponse.MemberCalendarStatus(
                            u.getId(), u.getName(), u.getEmail(), tokenRepo.existsByUser(u));
                }).collect(Collectors.toList());

        return new CalendarConnectionStatusResponse(myConnected, statuses);
    }

    @Transactional
    public void disconnect(User user) {
        tokenRepo.findByUser(user).ifPresent(tokenRepo::delete);
    }

    // ── private helpers ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<?, ?> exchangeCodeForTokens(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        return webClient.post()
                .uri(GOOGLE_TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");

        return webClient.post()
                .uri(GOOGLE_TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                // Google이 4xx/5xx 반환할 경우 (토큰 만료/취소/스코프 문제)
                // 예외를 널리지 않고 빈 맵으로 대체해 호출자가 안전하게 처리하게 함
                .onErrorReturn(Map.of())
                .block();
    }

    @Transactional
    public String ensureFreshToken(GoogleCalendarToken token) {
        if (token.isExpired() && token.getRefreshToken() != null) {
            Map<?, ?> resp = refreshAccessToken(token.getRefreshToken());
            // 갱신 성공 여부를 access_token 필드로 판단
            String newAccessToken = (String) resp.get("access_token");
            if (newAccessToken != null) {
                token.setAccessToken(newAccessToken);
                Number exp = (Number) resp.get("expires_in");
                if (exp != null) token.setTokenExpiry(OffsetDateTime.now().plusSeconds(exp.longValue()));
                tokenRepo.save(token);
            }
            // newAccessToken == null 이면 갱신 실패 → 기존 토큰 유지
            // (fetchBusySlots는 onErrorReturn으로 보호되어 빈 목록을 안전하게 반환)
        }
        return token.getAccessToken();
    }

    @SuppressWarnings("unchecked")
    private List<CalendarAvailabilityResponse.BusySlot> fetchBusySlots(String accessToken, String timeMin, String timeMax, String memberName) {
        Map<String, Object> body = Map.of(
                "timeMin", timeMin,
                "timeMax", timeMax,
                "items", List.of(Map.of("id", "primary"))
        );

        Map<?, ?> resp = webClient.post()
                .uri(GOOGLE_CAL_URL + "/freeBusy")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorReturn(Map.of())
                .block();

        if (resp == null) return List.of();

        List<CalendarAvailabilityResponse.BusySlot> slots = new ArrayList<>();
        try {
            Map<?, ?> calendars = (Map<?, ?>) resp.get("calendars");
            if (calendars == null) return List.of();
            Map<?, ?> primary = (Map<?, ?>) calendars.get("primary");
            if (primary == null) return List.of();
            List<?> busy = (List<?>) primary.get("busy");
            if (busy == null) return List.of();
            for (Object b : busy) {
                Map<?, ?> slot = (Map<?, ?>) b;
                slots.add(new CalendarAvailabilityResponse.BusySlot(
                        (String) slot.get("start"), (String) slot.get("end"), memberName));
            }
        } catch (Exception ignored) {}
        return slots;
    }

    private List<CalendarAvailabilityResponse.FreeSlot> computeFreeSlots(
            List<CalendarAvailabilityResponse.BusySlot> busy, LocalDate date) {
        // 09:00 ~ 22:00 범위에서 바쁜 시간 제외
        ZoneId tz = ZoneId.of("Asia/Seoul");
        ZonedDateTime start = date.atTime(9, 0).atZone(tz);
        ZonedDateTime end   = date.atTime(22, 0).atZone(tz);

        List<ZonedDateTime[]> busyRanges = busy.stream().map(b -> new ZonedDateTime[]{
                ZonedDateTime.parse(b.start()),
                ZonedDateTime.parse(b.end())
        }).sorted(Comparator.comparing(r -> r[0])).collect(Collectors.toList());

        List<CalendarAvailabilityResponse.FreeSlot> free = new ArrayList<>();
        ZonedDateTime cur = start;
        for (ZonedDateTime[] range : busyRanges) {
            if (range[0].isAfter(cur)) {
                long mins = Duration.between(cur, range[0]).toMinutes();
                if (mins >= 30) {
                    free.add(new CalendarAvailabilityResponse.FreeSlot(
                            cur.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            range[0].format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            (int) mins));
                }
            }
            if (range[1].isAfter(cur)) cur = range[1];
        }
        if (cur.isBefore(end)) {
            long mins = Duration.between(cur, end).toMinutes();
            if (mins >= 30) {
                free.add(new CalendarAvailabilityResponse.FreeSlot(
                        cur.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        (int) mins));
            }
        }
        return free;
    }

    private void createGoogleEvent(String accessToken, CalendarEventRequest req, List<User> attendees) {
        List<Map<String, String>> googleAttendees = attendees.stream()
                .map(u -> Map.of("email", u.getEmail()))
                .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", req.title());
        body.put("description", req.description() != null ? req.description() : "");
        body.put("start", Map.of("dateTime", req.startTime(), "timeZone", "Asia/Seoul"));
        body.put("end",   Map.of("dateTime", req.endTime(),   "timeZone", "Asia/Seoul"));
        body.put("attendees", googleAttendees);

        webClient.post()
                .uri(GOOGLE_CAL_URL + "/calendars/primary/events?sendUpdates=all")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorComplete()
                .block();
    }

    // ── 점수제 헬퍼 ──────────────────────────────────────────────────────

    /** Google Calendar Events API로 이벤트 가져오기 (allDay 여부 포함) */
    @SuppressWarnings("unchecked")
    private List<CalendarEventInfo> fetchCalendarEventsWithTypes(
            String accessToken, String timeMin, String timeMax, String memberName) {

        // + 기호가 포함된 timeMin/timeMax를 URL-safe하게 인코딩
        String uri = UriComponentsBuilder
                .fromHttpUrl(GOOGLE_CAL_URL + "/calendars/primary/events")
                .queryParam("timeMin", timeMin)
                .queryParam("timeMax", timeMax)
                .queryParam("singleEvents", "true")
                .queryParam("orderBy", "startTime")
                .queryParam("maxResults", "250")
                .build()
                .encode()          // +09:00 → %2B09%3A00 명시적 인코딩
                .toUriString();

        Map<?, ?> resp = webClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorReturn(Map.of())
                .block();

        if (resp == null) return List.of();

        List<CalendarEventInfo> events = new ArrayList<>();
        ZoneId kst = ZoneId.of("Asia/Seoul");

        List<?> items = (List<?>) resp.get("items");
        if (items == null) return List.of();

        for (Object obj : items) {
            try {
                Map<?, ?> event = (Map<?, ?>) obj;
                if ("cancelled".equals(event.get("status"))) continue;
                String summary = (String) event.get("summary");
                Map<?, ?> start = (Map<?, ?>) event.get("start");
                Map<?, ?> end   = (Map<?, ?>) event.get("end");
                if (start == null || end == null) continue;

                String startDT   = (String) start.get("dateTime");
                String startDate = (String) start.get("date");
                String endDT     = (String) end.get("dateTime");
                String endDate   = (String) end.get("date");

                if (startDT != null && endDT != null) {
                    // HARD BLOCK: 시간이 명시된 일정
                    events.add(new CalendarEventInfo(memberName, false,
                            ZonedDateTime.parse(startDT).withZoneSameInstant(kst),
                            ZonedDateTime.parse(endDT).withZoneSameInstant(kst)));
                } else if (startDate != null) {
                    // SOFT BLOCK: 종일 일정 (endDate는 exclusive, 없으면 당일+1)
                    String resolvedEnd = (endDate != null) ? endDate
                            : LocalDate.parse(startDate).plusDays(1).toString();
                    events.add(new CalendarEventInfo(memberName, true,
                            LocalDate.parse(startDate).atStartOfDay(kst),
                            LocalDate.parse(resolvedEnd).atStartOfDay(kst)));
                }
            } catch (Exception e) {
                log.warn("[CAL-DEBUG] {} 이벤트 파싱 실패 (건너뜀): {}", memberName, e.getMessage());
            }
        }
        return events;
    }

    /** 09:00–18:00, 1시간 단위 슬롯 생성 후 HARD/SOFT 기준 점수 계산 */
    private List<ScoredSlot> computeScoredSlots(List<CalendarEventInfo> events,
                                                 LocalDate startDate, int days) {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        List<ScoredSlot> result = new ArrayList<>();

        for (int dayOffset = 0; dayOffset < days; dayOffset++) {
            LocalDate day = startDate.plusDays(dayOffset);
            for (int hour = 9; hour < 18; hour++) {
                ZonedDateTime slotStart = day.atTime(hour, 0).atZone(kst);
                ZonedDateTime slotEnd   = slotStart.plusHours(1);

                // HARD BLOCK: 시간 일정이 겹치면 후보에서 제외
                boolean hardBlocked = events.stream().anyMatch(e -> !e.allDay()
                        && e.start().isBefore(slotEnd)
                        && e.end().isAfter(slotStart));
                if (hardBlocked) continue;

                // SOFT BLOCK: 종일 일정 보유 팀원 목록 (슬롯 날짜가 이벤트 범위 안인지)
                LocalDate slotDate = slotStart.toLocalDate();
                List<String> softBlockMembers = events.stream()
                        .filter(e -> e.allDay()
                                && !slotDate.isBefore(e.start().toLocalDate())
                                && slotDate.isBefore(e.end().toLocalDate()))
                        .map(CalendarEventInfo::memberName)
                        .distinct()
                        .collect(Collectors.toList());

                int score = Math.max(0, 100 - softBlockMembers.size() * 30);
                result.add(new ScoredSlot(slotStart, score, softBlockMembers));
            }
        }

        // 점수 내림차순, 동점이면 날짜/시간 오름차순
        result.sort(Comparator.comparingInt(ScoredSlot::score).reversed()
                .thenComparing(s -> s.start()));
        return result;
    }

    /** AI에게 이유(reason)만 생성 요청 — 슬롯 선택은 서버가 완료 */
    private String buildScoredSlotPrompt(List<String> memberNames, List<ScoredSlot> slots,
                                          String deadline) {
        DateTimeFormatter disp = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm")
                .withLocale(java.util.Locale.KOREAN);
        StringBuilder sb = new StringBuilder();
        sb.append("서버가 계산한 회의 추천 후보 슬롯 ").append(slots.size())
          .append("개에 대해 자연스러운 한국어 추천 이유를 1~2문장으로 작성해라.\n");
        sb.append("주어진 슬롯 외의 시간은 절대 만들지 말 것.\n\n");
        sb.append("팀원: ").append(String.join(", ", memberNames)).append("\n");
        if (deadline != null) sb.append("마감일: ").append(deadline).append("\n");
        sb.append("\n후보 슬롯:\n");
        for (int i = 0; i < slots.size(); i++) {
            ScoredSlot s = slots.get(i);
            sb.append(i + 1).append(". ").append(s.start().format(disp))
              .append(" (추천도 ").append(s.score()).append(")");
            if (!s.softBlockMembers().isEmpty()) {
                sb.append(" — ").append(String.join(", ", s.softBlockMembers())).append("님 종일 일정");
            }
            sb.append("\n");
        }
        sb.append("\n반드시 아래 JSON으로만 응답해. 다른 텍스트 없이 JSON만:\n");
        sb.append("{\"reasons\":[\"슬롯1 이유\",\"슬롯2 이유\"");
        if (slots.size() >= 3) sb.append(",\"슬롯3 이유\"");
        sb.append("]}");
        return sb.toString();
    }

    /** {"reasons":["...","...","..."]} 배열 파싱 */
    private String[] parseReasonArray(String raw) {
        if (raw == null || raw.isBlank()) return new String[0];
        try {
            int jsonStart = raw.indexOf('{');
            int jsonEnd   = raw.lastIndexOf('}');
            if (jsonStart < 0 || jsonEnd < 0) return new String[0];
            String json = raw.substring(jsonStart, jsonEnd + 1);

            int arrStart = json.indexOf('[');
            int arrEnd   = json.lastIndexOf(']');
            if (arrStart < 0 || arrEnd <= arrStart) return new String[0];

            String content = json.substring(arrStart + 1, arrEnd);
            List<String> reasons = new ArrayList<>();
            int pos = 0;
            while (pos < content.length()) {
                int q1 = content.indexOf('"', pos);
                if (q1 < 0) break;
                int q2 = q1 + 1;
                while (q2 < content.length()) {
                    char c = content.charAt(q2);
                    if (c == '\\') { q2 += 2; continue; }
                    if (c == '"') break;
                    q2++;
                }
                if (q2 < content.length()) reasons.add(content.substring(q1 + 1, q2));
                pos = q2 + 1;
            }
            return reasons.toArray(new String[0]);
        } catch (Exception e) {
            return new String[0];
        }
    }

    /** AI 실패 시 시간대 기반 기본 이유 */
    private String defaultReason(ScoredSlot slot) {
        if (!slot.softBlockMembers().isEmpty()) {
            String names = String.join(", ", slot.softBlockMembers());
            return names + "님 종일 일정이 있어 확인이 필요하지만, 시간 일정 충돌 없이 가능한 시간입니다.";
        }
        int hour = slot.start().getHour();
        if (hour < 12) return "오전 시간대로 집중력이 높아 회의 효율이 좋습니다.";
        if (hour < 15) return "점심 후 활기차게 시작하기 좋은 오후 초반입니다.";
        return "업무 마무리 전 진행 상황을 점검하기 좋은 오후 시간대입니다.";
    }

    /** 전원 가능 슬롯 목록만 후보로 전달 — AI가 목록 외 시간 추천 불가 */
    private String buildFreeSlotPrompt(List<String> memberNames, List<String> freeSlots,
                                       String deadline, String targetDate) {
        String freeStr    = String.join("\n", freeSlots);
        String deadlineStr = deadline != null ? deadline : "미정";
        return """
                팀원 전원이 비어 있는 시간 목록이 아래에 있다.
                반드시 이 목록 중에서만 회의하기 좋은 시간 최대 3개를 골라서 추천해줘.
                목록에 없는 시간은 절대 추천하지 말 것.
                각 추천에 이유도 같이 설명해줘.

                팀원: %s
                프로젝트 마감일: %s
                기준 날짜: %s

                전원 가능한 시간 (이 중에서만 선택할 것):
                %s

                반드시 아래 JSON 형식으로만 응답해. 다른 텍스트 없이 JSON만:
                {"recommendations":[{"time":"2025-01-20T14:00:00+09:00","durationMinutes":60,"reason":"이유"},{"time":"...","durationMinutes":60,"reason":"..."},{"time":"...","durationMinutes":60,"reason":"..."}]}
                """.formatted(String.join(", ", memberNames), deadlineStr, targetDate, freeStr);
    }

    private String buildRecommendPrompt(List<String> memberNames, List<String> busySlots,
                                        String deadline, String targetDate) {
        String busyStr = busySlots.isEmpty() ? "없음 (캘린더 미연동)" : String.join("\n", busySlots);
        String deadlineStr = deadline != null ? deadline : "미정";
        return """
                다음 팀원들의 바쁜 시간과 프로젝트 마감일을 고려해서 회의하기 좋은 시간 3개를 추천해줘.
                각 추천에 이유도 같이 설명해줘.
                시간은 %s 이후로 잡아줘.

                팀원: %s

                바쁜 시간대:
                %s

                프로젝트 마감일: %s

                반드시 아래 JSON 형식으로만 응답해. 다른 텍스트 없이 JSON만:
                {"recommendations":[{"time":"2025-01-20T14:00:00+09:00","durationMinutes":60,"reason":"이유"},{"time":"...","durationMinutes":60,"reason":"..."},{"time":"...","durationMinutes":60,"reason":"..."}]}
                """.formatted(targetDate, String.join(", ", memberNames), busyStr, deadlineStr);
    }

    private CalendarRecommendResponse parseRecommendations(String raw) {
        try {
            int start = raw.indexOf('{');
            int end   = raw.lastIndexOf('}') + 1;
            if (start < 0 || end <= start) return fallbackRecommendations();
            return parseJsonManually(raw.substring(start, end));
        } catch (Exception e) {
            return fallbackRecommendations();
        }
    }

    private CalendarRecommendResponse parseJsonManually(String json) {
        // 간단한 수동 파싱 — Jackson ObjectMapper 없이
        List<CalendarRecommendResponse.Recommendation> recs = new ArrayList<>();
        try {
            // "time":"..." 패턴 추출
            String[] timeParts   = extractJsonArrayValues(json, "time");
            String[] reasonParts = extractJsonArrayValues(json, "reason");
            String[] durParts    = extractJsonArrayValues(json, "durationMinutes");

            for (int i = 0; i < Math.min(3, timeParts.length); i++) {
                int dur = 60;
                try { if (i < durParts.length) dur = Integer.parseInt(durParts[i].trim()); } catch (Exception ignored) {}
                recs.add(new CalendarRecommendResponse.Recommendation(
                        timeParts[i], dur,
                        i < reasonParts.length ? reasonParts[i] : "팀원 스케줄 분석 결과",
                        i + 1));
            }
        } catch (Exception ignored) {}
        return recs.isEmpty() ? fallbackRecommendations() : new CalendarRecommendResponse(recs);
    }

    private String[] extractJsonArrayValues(String json, String key) {
        List<String> vals = new ArrayList<>();
        String search = "\"" + key + "\":";
        int pos = 0;
        while ((pos = json.indexOf(search, pos)) >= 0) {
            pos += search.length();
            char first = json.charAt(pos);
            if (first == '"') {
                int endQ = json.indexOf('"', pos + 1);
                if (endQ > pos) vals.add(json.substring(pos + 1, endQ));
            } else {
                int endNum = pos;
                while (endNum < json.length() && (Character.isDigit(json.charAt(endNum)) || json.charAt(endNum) == '.')) endNum++;
                vals.add(json.substring(pos, endNum));
            }
        }
        return vals.toArray(new String[0]);
    }

    private CalendarRecommendResponse fallbackRecommendations() {
        ZonedDateTime base = LocalDate.now().plusDays(1).atTime(10, 0).atZone(ZoneId.of("Asia/Seoul"));
        DateTimeFormatter fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        return new CalendarRecommendResponse(List.of(
                new CalendarRecommendResponse.Recommendation(base.format(fmt), 60, "오전 시간대는 집중력이 높아 회의 효율이 좋습니다", 1),
                new CalendarRecommendResponse.Recommendation(base.plusDays(1).withHour(14).format(fmt), 60, "오후 초반은 점심 이후 활동적인 시간대입니다", 2),
                new CalendarRecommendResponse.Recommendation(base.plusDays(2).withHour(16).format(fmt), 60, "마감일을 고려한 여유 있는 오후 시간대입니다", 3)
        ));
    }

    private LocalDate parseDate(String date) {
        return switch (date) {
            case "this_week" -> LocalDate.now();
            case "next_week" -> LocalDate.now().plusWeeks(1);
            default -> LocalDate.parse(date);
        };
    }

    private String resolveTargetDate(String target) {
        return switch (target) {
            case "this_week" -> LocalDate.now().toString();
            case "next_week" -> LocalDate.now().plusWeeks(1).toString();
            default -> target;
        };
    }
}
