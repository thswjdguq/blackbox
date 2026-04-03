package com.blackbox.controller;

import com.blackbox.dto.*;
import com.blackbox.entity.User;
import com.blackbox.entity.Meeting;
import com.blackbox.service.ClaudeService;
import com.blackbox.service.MeetingService;
import com.blackbox.service.NotionService;
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
    private final NotionService  notionService;

    public MeetingController(MeetingService meetingService,
                             ClaudeService claudeService,
                             NotionService notionService) {
        this.meetingService = meetingService;
        this.claudeService  = claudeService;
        this.notionService  = notionService;
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
        String summary = claudeService.summarizeMeeting(
                meeting.title(), meeting.purpose(), meeting.notes(), meeting.decisions());
        return ResponseEntity.ok(new AiSummaryResponse(summary));
    }

    @PostMapping("/api/projects/{projectId}/meetings/{meetingId}/ai/extract-actions")
    public ResponseEntity<AiActionItemsResponse> extractActions(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal User user) {
        MeetingResponse meeting = meetingService.getMeeting(projectId, meetingId, user);
        List<String> items = claudeService.extractActionItems(meeting.notes(), meeting.decisions());
        return ResponseEntity.ok(new AiActionItemsResponse(items));
    }

    // ── Notion 내보내기 ───────────────────────────────────────────────────

    @PostMapping("/api/projects/{projectId}/meetings/{meetingId}/notion/export")
    public ResponseEntity<NotionExportResponse> exportToNotion(
            @PathVariable UUID projectId,
            @PathVariable UUID meetingId,
            @AuthenticationPrincipal User user) {
        MeetingResponse meetingDto = meetingService.getMeeting(projectId, meetingId, user);

        // AI 요약 생성 (API 키 없으면 null)
        String aiSummary = null;
        try {
            aiSummary = claudeService.summarizeMeeting(
                    meetingDto.title(), meetingDto.purpose(),
                    meetingDto.notes(), meetingDto.decisions());
        } catch (Exception ignored) { }

        // Meeting 엔티티 직접 조회 (NotionService에서 사용)
        Meeting meeting = meetingService.getRawMeeting(projectId, meetingId, user);
        String pageUrl = notionService.exportMeeting(meeting, aiSummary);

        return ResponseEntity.ok(new NotionExportResponse(pageUrl));
    }

    // ── Global checkin endpoint (코드만으로 체크인) ────────────────────────

    @PostMapping("/api/meetings/checkin")
    public ResponseEntity<AttendeeResponse> checkin(
            @Valid @RequestBody CheckinRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(meetingService.checkin(req, user));
    }
}
