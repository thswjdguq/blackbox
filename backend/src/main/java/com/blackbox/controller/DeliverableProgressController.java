package com.blackbox.controller;

import com.blackbox.dto.DeliverableProgressDtos.Response;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/deliverables")
public class DeliverableProgressController {
    private final DeliverableProgressService service;

    @GetMapping("/{deliverableId}/progress")
    public Response get(
            @PathVariable UUID projectId,
            @PathVariable UUID deliverableId,
            @AuthenticationPrincipal User user
    ) {
        return service.get(projectId, deliverableId, user);
    }
}
