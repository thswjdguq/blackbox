package com.blackbox.controller;

import com.blackbox.dto.TaskResponse;
import com.blackbox.entity.User;
import com.blackbox.service.TaskWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/projects/{projectId}/tasks/{taskId}")
public class TaskWorkflowController {
    public record Decision(@NotNull Boolean approved, Boolean criteriaConfirmed, @Size(max=2000) String reason) {}
    public record Reopen(@NotBlank @Size(max=2000) String reason) {}
    private final TaskWorkflowService service;
    public TaskWorkflowController(TaskWorkflowService service) { this.service=service; }
    @GetMapping("/completion") public TaskWorkflowService.Completion view(@PathVariable UUID projectId,@PathVariable UUID taskId,@AuthenticationPrincipal User user) { return service.view(projectId,taskId,user); }
    @PostMapping("/completion-request") public TaskResponse request(@PathVariable UUID projectId,@PathVariable UUID taskId,@AuthenticationPrincipal User user) { return service.request(projectId,taskId,user); }
    @PostMapping("/completion-cancel") public TaskResponse cancel(@PathVariable UUID projectId,@PathVariable UUID taskId,@Valid @RequestBody Reopen req,@AuthenticationPrincipal User user) { return service.cancel(projectId,taskId,req.reason(),user); }
    @PutMapping("/completion-decision") public TaskResponse decide(@PathVariable UUID projectId,@PathVariable UUID taskId,@Valid @RequestBody Decision req,@AuthenticationPrincipal User user) {
        return service.decide(projectId,taskId,req.approved(),Boolean.TRUE.equals(req.criteriaConfirmed()),req.reason(),user);
    }
    @PostMapping("/reopen") public TaskResponse reopen(@PathVariable UUID projectId,@PathVariable UUID taskId,@Valid @RequestBody Reopen req,@AuthenticationPrincipal User user) { return service.reopen(projectId,taskId,req.reason(),user); }
}
