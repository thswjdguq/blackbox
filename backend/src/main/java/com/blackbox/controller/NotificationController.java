package com.blackbox.controller;

import com.blackbox.dto.NotificationDtos;
import com.blackbox.entity.User;
import com.blackbox.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** K-54. 받는 사람은 항상 로그인 사용자다. 클라이언트가 recipientId를 지정하지 않는다. */
@RestController
@RequestMapping("/api/projects/{projectId}/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    public NotificationDtos.Page list(@PathVariable UUID projectId, @RequestParam(required = false) Integer limit,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) Boolean unreadOnly,
                                      @AuthenticationPrincipal User user) {
        return service.list(projectId, limit, cursor, unreadOnly, user);
    }

    @GetMapping("/unread-count")
    public NotificationDtos.UnreadCount unreadCount(@PathVariable UUID projectId, @AuthenticationPrincipal User user) {
        return service.unread(projectId, user);
    }

    @PutMapping("/{notificationId}/read")
    public NotificationDtos.ReadResult read(@PathVariable UUID projectId, @PathVariable UUID notificationId,
                                            @AuthenticationPrincipal User user) {
        return service.markRead(projectId, notificationId, user);
    }
}
