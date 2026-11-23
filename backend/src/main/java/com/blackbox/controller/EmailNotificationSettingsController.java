package com.blackbox.controller;

import com.blackbox.dto.EmailNotificationSettingsDtos;
import com.blackbox.entity.User;
import com.blackbox.service.EmailNotificationSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/email-settings")
public class EmailNotificationSettingsController {
    private final EmailNotificationSettingsService service;
    public EmailNotificationSettingsController(EmailNotificationSettingsService service) { this.service = service; }

    @GetMapping
    public EmailNotificationSettingsDtos.View get(@AuthenticationPrincipal User user) {
        return service.get(userId(user));
    }

    @PutMapping
    public EmailNotificationSettingsDtos.View update(@RequestBody EmailNotificationSettingsDtos.Update request,
            @AuthenticationPrincipal User user) {
        UUID id = userId(user);
        return service.update(id, request.requiredEnabled());
    }

    private UUID userId(User user) {
        if (user == null || user.getId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        return user.getId();
    }
}
