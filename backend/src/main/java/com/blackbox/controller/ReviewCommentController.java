package com.blackbox.controller;

import com.blackbox.dto.ReviewCommentDtos.*;
import com.blackbox.entity.User;
import com.blackbox.service.ReviewCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/deliverables/{deliverableId}/reviews/{reviewId}/comments")
public class ReviewCommentController {
    private final ReviewCommentService service;
    @GetMapping
    public List<Response> list(@PathVariable UUID projectId, @PathVariable UUID deliverableId, @PathVariable UUID reviewId, @AuthenticationPrincipal User user) {
        return service.list(projectId, deliverableId, reviewId, user);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Response create(@PathVariable UUID projectId, @PathVariable UUID deliverableId, @PathVariable UUID reviewId, @RequestBody CreateRequest request, @AuthenticationPrincipal User user) {
        return service.create(projectId, deliverableId, reviewId, request, user);
    }
    @PutMapping("/{commentId}/resolution")
    public Response resolve(@PathVariable UUID projectId, @PathVariable UUID deliverableId, @PathVariable UUID reviewId, @PathVariable UUID commentId, @RequestBody ResolutionRequest request, @AuthenticationPrincipal User user) {
        return service.resolve(projectId, deliverableId, reviewId, commentId, request, user);
    }
    @DeleteMapping("/{commentId}/resolution")
    public Response reopen(@PathVariable UUID projectId, @PathVariable UUID deliverableId, @PathVariable UUID reviewId, @PathVariable UUID commentId, @AuthenticationPrincipal User user) {
        return service.reopen(projectId, deliverableId, reviewId, commentId, user);
    }
}
