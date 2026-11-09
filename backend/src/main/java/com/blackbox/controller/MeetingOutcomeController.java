package com.blackbox.controller;

import com.blackbox.entity.User;
import com.blackbox.service.MeetingOutcomeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class MeetingOutcomeController {
    private final MeetingOutcomeService service;
    public MeetingOutcomeController(MeetingOutcomeService service) { this.service = service; }
    @PostMapping("/api/projects/{projectId}/meetings/{meetingId}/ai/extract-outcomes")
    public MeetingOutcomeService.Outcomes extract(@PathVariable UUID projectId, @PathVariable UUID meetingId, @AuthenticationPrincipal User user) {
        return service.extract(projectId, meetingId, user);
    }
}
