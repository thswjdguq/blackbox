package com.blackbox.controller;

import com.blackbox.dto.*;
import com.blackbox.entity.User;
import com.blackbox.entity.Meeting;
import com.blackbox.service.ClaudeService;
import com.blackbox.service.MeetingService;
import com.blackbox.service.NotionService;
import com.blackbox.service.OpenAiService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class MeetingController {

    private final MeetingService meetingService;
    private final ClaudeService  claudeService;
    private final OpenAiService  openAiService;
    private final NotionService  notionService;

    public MeetingController(MeetingService meetingService,
                             ClaudeService claudeService,
                             OpenAiService openAiService,
                             NotionService notionService) {
        this.meetingService = meetingService;
        this.claudeService  = claudeService;
        this.openAiService  = openAiService;
        this.notionService  = notionService;
    }

    /** Claude → OpenAI 순으로 사용 가능한 AI 서비스 선택 */
    private String aiSummarize(String title, String purpose, String notes, String decisions) {
        if (claudeService.isConfigured()) {
            return claudeService.summarizeMeeting(title, purpose, notes, decisions);
        } else if (openAiService.isConfigured()) {
            return openAiService.summarizeMeeting(title, purpose, notes, decisions);
        }
        throw new IllegalStateException("AI API 키가 설정되지 않았습니다 (CLAUDE_API_KEY 또는 OPENAI_API_KEY 필요)");
    }

    private java.util.List<String> aiExtract(String notes, String decisions) {
        if (claudeService.isConfigured()) {
            return claudeService.extractActionItems(notes, decisions);
        } else if (openAiService.isConfigured()) {
            return openAiService.extractActionItems(notes, decisions);
        }
        throw new IllegalStateException("AI API 키가 설정되지 않았습니다 (CLAUDE_API_KEY 또는 OPENAI_API_KEY 필요)");
    }

    // ── Project-scoped meeting endpoints ─────────────────────────────────

    @PostMapping("/api/projects/{projectId}/meetings")
    public ResponseEntity<MeetingResponse> createMeeting(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateMeetingRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingService.createMeeting(projectId, req, user));
    }

    @GetMapping("/api/projects/{projectId}/meetings")
    public ResponseEntity<List<MeetingResponse>> listMeetings(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(meetingService.listMeetings(projectId, user));
    }

    @GetMapping("/api/projects/{projectId}/meetings/{meetingId}")
    public ResponseEntity<MeetingResponse> getMeeting(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(meetingService.getMeeting(projectId, meetingId, user));
    }

    @PatchMapping("/api/projects/{projectId}/meetings/{meetingId}")
    public ResponseEntity<MeetingResponse> updateMeeting(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @Valid @RequestBody UpdateMeetingRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(meetingService.updateMeeting(projectId, meetingId, req, user));
    }

    @DeleteMapping("/api/projects/{projectId}/meetings/{meetingId}")
    public ResponseEntity<Void> deleteMeeting(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal User user) {
        meetingService.deleteMeeting(projectId, meetingId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/projects/{projectId}/meetings/{meetingId}/checkin-code/regenerate")
    public ResponseEntity<MeetingResponse> regenerateCheckinCode(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(meetingService.regenerateCheckinCode(projectId, meetingId, user));
    }

    @GetMapping("/api/projects/{projectId}/meetings/{meetingId}/attendees")
    public ResponseEntity<List<AttendeeResponse>> listAttendees(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(meetingService.listAttendees(projectId, meetingId, user));
    }

    @PostMapping("/api/projects/{projectId}/meetings/{meetingId}/action-items")
    public ResponseEntity<TaskResponse> createActionItem(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @Valid @RequestBody CreateTaskRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.createActionItem(projectId, meetingId, req, user));
    }

    // ── AI 엔드포인트 ────────────────────────────────────────────────────────

    @PostMapping("/api/projects/{projectId}/meetings/{meetingId}/ai/summarize")
    public ResponseEntity<AiSummaryResponse> summarizeMeeting(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal User user) {
        MeetingResponse meeting = meetingService.getMeeting(projectId, meetingId, user);
        String summary = aiSummarize(
                meeting.title(), meeting.purpose(), meeting.notes(), meeting.decisions());
        meetingService.saveAiSummary(projectId, meetingId, summary, user);
        return ResponseEntity.ok(new AiSummaryResponse(summary));
    }

    @PostMapping("/api/projects/{projectId}/meetings/{meetingId}/ai/extract-actions")
    public ResponseEntity<AiActionItemsResponse> extractActions(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal User user) {
        MeetingResponse meeting = meetingService.getMeeting(projectId, meetingId, user);
        List<String> items = aiExtract(meeting.notes(), meeting.decisions());
        return ResponseEntity.ok(new AiActionItemsResponse(items));
    }

    // ── Notion 내보내기 ───────────────────────────────────────────────────

    @PostMapping("/api/projects/{projectId}/meetings/{meetingId}/notion/export")
    public ResponseEntity<NotionExportResponse> exportToNotion(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @org.springframework.web.bind.annotation.RequestBody(required = false)
                java.util.Map<String, String> body,
            @AuthenticationPrincipal User user) {

        // 프론트에서 AI 요약을 미리 실행했다면 함께 포함, 아니면 null (AI 호출 없음)
        String aiSummary = (body != null) ? body.get("aiSummary") : null;

        Meeting meeting = meetingService.getRawMeeting(projectId, meetingId, user);
        NotionService.NotionExportResult result = notionService.exportMeeting(meeting, aiSummary);
        meetingService.saveNotionInfo(projectId, meetingId, result.pageId(), user);

        return ResponseEntity.ok(new NotionExportResponse(result.pageUrl()));
    }

    // ── Notion 캘린더 동기화 ─────────────────────────────────────────────────

    @PostMapping("/api/projects/{projectId}/meetings/{meetingId}/notion/calendar")
    public ResponseEntity<NotionExportResponse> syncToCalendar(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal User user) {
        Meeting meeting = meetingService.getRawMeeting(projectId, meetingId, user);
        String pageUrl = notionService.syncMeetingToCalendar(meeting);
        return ResponseEntity.ok(new NotionExportResponse(pageUrl));
    }

    // ── 회의 시간 추천 ───────────────────────────────────────────────────────

    @GetMapping("/api/projects/{projectId}/meetings/recommend")
    public ResponseEntity<MeetingRecommendResponse> recommendTime(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(meetingService.recommendMeetingTime(projectId, user));
    }

    // ── Global checkin endpoint (코드만으로 체크인) ────────────────────────

    @PostMapping("/api/meetings/checkin")
    public ResponseEntity<AttendeeResponse> checkin(
            @Valid @RequestBody CheckinRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(meetingService.checkin(req, user));
    }

    // ── 내 체크인 내역 (프로젝트 멤버십 무관) ──────────────────────────────

    @GetMapping("/api/my/checkins")
    public ResponseEntity<List<AttendeeResponse>> myCheckins(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(meetingService.myCheckins(user));
    }
}
