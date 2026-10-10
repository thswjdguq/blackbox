package com.blackbox.controller;

import com.blackbox.dto.ConfirmationDtos.Response;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableConfirmationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/deliverables/{deliverableId}")
public class DeliverableConfirmationController {
    private final DeliverableConfirmationService service;
    @GetMapping("/confirmation") public Response get(@PathVariable UUID projectId, @PathVariable UUID deliverableId,
            @AuthenticationPrincipal User user) {
        return service.get(projectId, deliverableId, user);
    }
}
