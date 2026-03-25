package com.blackbox.controller;

import com.blackbox.dto.*;
import com.blackbox.entity.User;
import com.blackbox.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTaskRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(projectId, req, user));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> listTasks(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(taskService.listTasks(projectId, status, user));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(taskService.getTask(projectId, taskId, user));
    }

    @PatchMapping("/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(taskService.updateTask(projectId, taskId, req, user));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal User user) {
        taskService.deleteTask(projectId, taskId, user);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskResponse> updateStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskStatusRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(taskService.updateStatus(projectId, taskId, req, user));
    }

    @PutMapping("/{taskId}/assignees")
    public ResponseEntity<TaskResponse> setAssignees(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody AssignTaskRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(taskService.setAssignees(projectId, taskId, req, user));
    }
}
