package com.blackbox.controller;

import com.blackbox.dto.DiscordSettingsResponse;
import com.blackbox.dto.UpdateDiscordSettingsRequest;
import com.blackbox.entity.DiscordSettings;
import com.blackbox.entity.User;
import com.blackbox.repository.DiscordSettingsRepository;
import com.blackbox.service.DiscordNotificationService;
import com.blackbox.service.ProjectAccessChecker;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/discord")
public class DiscordSettingsController {

    private final DiscordSettingsRepository settingsRepo;
    private final DiscordNotificationService discordService;
    private final ProjectAccessChecker accessChecker;

    public DiscordSettingsController(DiscordSettingsRepository settingsRepo,
                                     DiscordNotificationService discordService,
                                     ProjectAccessChecker accessChecker) {
        this.settingsRepo   = settingsRepo;
        this.discordService = discordService;
        this.accessChecker  = accessChecker;
    }

    // GET /api/projects/{projectId}/discord
    @GetMapping
    public ResponseEntity<DiscordSettingsResponse> getSettings(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal User user) {
        accessChecker.requireMember(accessChecker.getProject(projectId), user);
        DiscordSettings settings = settingsRepo.findById(projectId)
                .orElseGet(() -> DiscordSettings.defaults(projectId));
        return ResponseEntity.ok(DiscordSettingsResponse.from(settings));
    }

    // PUT /api/projects/{projectId}/discord
    @PutMapping
    public ResponseEntity<DiscordSettingsResponse> updateSettings(
            @PathVariable UUID projectId,
            @RequestBody UpdateDiscordSettingsRequest req,
            @AuthenticationPrincipal User user) {
        accessChecker.requireLeader(accessChecker.getProject(projectId), user);
        DiscordSettings settings = settingsRepo.findById(projectId)
                .orElseGet(() -> DiscordSettings.defaults(projectId));

        if (req.webhookUrl()           != null) settings.setWebhookUrl(req.webhookUrl());
        if (req.notifyTaskAssign()     != null) settings.setNotifyTaskAssign(req.notifyTaskAssign());
        if (req.notifyTaskDone()       != null) settings.setNotifyTaskDone(req.notifyTaskDone());
        if (req.notifyMeetingCreate()  != null) settings.setNotifyMeetingCreate(req.notifyMeetingCreate());
        if (req.notifyMeetingReminder()!= null) settings.setNotifyMeetingReminder(req.notifyMeetingReminder());
        if (req.notifyAlert()          != null) settings.setNotifyAlert(req.notifyAlert());

        settingsRepo.save(settings);
        return ResponseEntity.ok(DiscordSettingsResponse.from(settings));
    }

    // POST /api/projects/{projectId}/discord/test
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testWebhook(
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {
        accessChecker.requireLeader(accessChecker.getProject(projectId), user);
        String webhookUrl = body.get("webhookUrl");
        boolean ok = discordService.test(webhookUrl);
        return ResponseEntity.ok(Map.of("success", ok,
                "message", ok ? "테스트 메시지 전송 성공" : "Webhook URL이 올바르지 않거나 전송에 실패했습니다"));
    }
}
