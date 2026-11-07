package com.blackbox.controller;

import com.blackbox.entity.User;
import com.blackbox.service.TaskMessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class TaskMessageController {
    public record Post(@NotBlank @Size(max=2000) String body, UUID recipientId, UUID parentId, UUID fileId) {}
    private final TaskMessageService service;
    public TaskMessageController(TaskMessageService service) { this.service=service; }
    @GetMapping("/api/projects/{projectId}/tasks/{taskId}/messages")
    public TaskMessageService.Thread list(@PathVariable UUID projectId,@PathVariable UUID taskId,@RequestParam(defaultValue="0") int page,@AuthenticationPrincipal User user) {
        return service.list(projectId,taskId,page,user);
    }
    @PostMapping("/api/projects/{projectId}/tasks/{taskId}/messages")
    public TaskMessageService.Message post(@PathVariable UUID projectId,@PathVariable UUID taskId,@Valid @RequestBody Post req,@AuthenticationPrincipal User user) {
        return service.post(projectId,taskId,req.body(),req.recipientId(),req.parentId(),req.fileId(),user);
    }
    @GetMapping("/api/projects/{projectId}/tasks/{taskId}/messages/{messageId}")
    public TaskMessageService.Message get(@PathVariable UUID projectId,@PathVariable UUID taskId,@PathVariable UUID messageId,@AuthenticationPrincipal User user) {
        return service.get(projectId,taskId,messageId,user);
    }
    @GetMapping("/api/projects/{projectId}/feedback")
    public TaskMessageService.Inbox inbox(@PathVariable UUID projectId,@AuthenticationPrincipal User user) { return service.inbox(projectId,user); }
}
