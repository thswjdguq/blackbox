package com.blackbox.controller;

import com.blackbox.dto.ReviewDtos.*;
import com.blackbox.entity.User;
import com.blackbox.service.ReviewRoundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/deliverables/{deliverableId}/reviews")
public class ReviewRoundController {
    private final ReviewRoundService service;
    @GetMapping public ListResponse list(@PathVariable UUID projectId, @PathVariable UUID deliverableId,
            @AuthenticationPrincipal User user) {
        return service.list(projectId, deliverableId, user);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Round open(@PathVariable UUID projectId, @PathVariable UUID deliverableId,
            @RequestBody OpenRequest req, @AuthenticationPrincipal User user) {
        return service.open(projectId, deliverableId, req, user);
    }
    @PutMapping("/{reviewId}/decision")
    public Round decide(@PathVariable UUID projectId, @PathVariable UUID deliverableId, @PathVariable UUID reviewId,
            @Valid @RequestBody DecisionRequest req, @AuthenticationPrincipal User user) {
        return service.decide(projectId, deliverableId, reviewId, req.decision(), user);
    }
}
