package com.blackbox.controller;

import com.blackbox.entity.User;
import com.blackbox.service.MeetingAttendanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/meetings/{meetingId}/attendance")
public class MeetingAttendanceController {
    public record Mark(@NotNull Boolean attending) {}
    public record Correction(@NotBlank String status) {}
    private final MeetingAttendanceService service;
    public MeetingAttendanceController(MeetingAttendanceService service) { this.service = service; }
    @GetMapping public MeetingAttendanceService.Summary list(@PathVariable UUID projectId, @PathVariable UUID meetingId, @AuthenticationPrincipal User user) {
        return service.list(projectId, meetingId, user);
    }
    @PutMapping public MeetingAttendanceService.Attendance mark(@PathVariable UUID projectId, @PathVariable UUID meetingId,
            @Valid @RequestBody Mark req, @AuthenticationPrincipal User user) {
        return service.mark(projectId, meetingId, req.attending(), user);
    }
    @PutMapping("/{userId}") public MeetingAttendanceService.Attendance correct(@PathVariable UUID projectId,
            @PathVariable UUID meetingId, @PathVariable UUID userId, @Valid @RequestBody Correction req, @AuthenticationPrincipal User user) {
        return service.correct(projectId, meetingId, userId, req.status(), user);
    }
}
