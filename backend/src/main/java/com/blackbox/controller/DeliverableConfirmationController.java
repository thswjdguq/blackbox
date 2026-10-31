package com.blackbox.controller;

import com.blackbox.dto.ConfirmationDtos.CancelRequest;
import com.blackbox.dto.ConfirmationDtos.ConfirmRequest;
import com.blackbox.dto.ConfirmationDtos.Response;
import com.blackbox.dto.ConfirmationDtos.SubmitRequest;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableConfirmationService;
import jakarta.validation.Valid;
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
    @PutMapping("/confirmation") public Response confirm(@PathVariable UUID projectId, @PathVariable UUID deliverableId,
            @Valid @RequestBody ConfirmRequest req, @AuthenticationPrincipal User user) {
        return service.confirm(projectId, deliverableId, req.reviewId(), user);
    }
    @PostMapping("/confirmation/cancellation") public Response cancel(@PathVariable UUID projectId,
            @PathVariable UUID deliverableId, @Valid @RequestBody CancelRequest req, @AuthenticationPrincipal User user) {
        return service.cancel(projectId, deliverableId, req.reviewId(), req.reason(), user);
    }
    @PutMapping("/submission") public Response submit(@PathVariable UUID projectId, @PathVariable UUID deliverableId,
            @Valid @RequestBody SubmitRequest req, @AuthenticationPrincipal User user) {
        return service.submit(projectId, deliverableId, req, user);
    }
}
