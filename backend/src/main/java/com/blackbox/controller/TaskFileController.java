package com.blackbox.controller;

import com.blackbox.entity.User;
import com.blackbox.service.TaskFileService;
import com.blackbox.dto.ReviewDtos;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController
public class TaskFileController {
    private final TaskFileService service;
    public TaskFileController(TaskFileService service) { this.service = service; }
    @GetMapping("/api/projects/{projectId}/tasks/{taskId}/files")
    public List<TaskFileService.Resource> list(@PathVariable UUID projectId, @PathVariable UUID taskId, @AuthenticationPrincipal User user) {
        return service.list(projectId, taskId, user);
    }
    @PostMapping("/api/projects/{projectId}/tasks/{taskId}/files")
    public TaskFileService.Resource upload(@PathVariable UUID projectId, @PathVariable UUID taskId,
            @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User user) throws IOException {
        return service.upload(projectId, taskId, file, user);
    }
    @PostMapping("/api/projects/{projectId}/tasks/{taskId}/files/{fileId}/review")
    public ReviewDtos.Round review(@PathVariable UUID projectId, @PathVariable UUID taskId, @PathVariable UUID fileId, @AuthenticationPrincipal User user) {
        return service.requestReview(projectId, taskId, fileId, user);
    }
    @GetMapping("/api/projects/{projectId}/deliverables/{deliverableId}/task-files")
    public List<TaskFileService.Resource> deliverable(@PathVariable UUID projectId, @PathVariable UUID deliverableId, @AuthenticationPrincipal User user) {
        return service.listDeliverable(projectId, deliverableId, user);
    }
}
