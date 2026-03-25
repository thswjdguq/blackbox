package com.blackbox.controller;

import com.blackbox.dto.*;
import com.blackbox.entity.User;
import com.blackbox.service.MeetingService;
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

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
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

    // ── Global checkin endpoint (코드만으로 체크인) ────────────────────────

    @PostMapping("/api/meetings/checkin")
    public ResponseEntity<AttendeeResponse> checkin(
            @Valid @RequestBody CheckinRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(meetingService.checkin(req, user));
    }
}
