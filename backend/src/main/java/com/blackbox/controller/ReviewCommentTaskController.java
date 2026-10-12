package com.blackbox.controller;

import com.blackbox.dto.ReviewCommentTaskDtos.*;
import com.blackbox.entity.User;
import com.blackbox.service.ReviewCommentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/deliverables/{deliverableId}/reviews/{reviewId}/comments/{commentId}/task")
public class ReviewCommentTaskController {
    private final ReviewCommentTaskService service;
    @PostMapping
    public ResponseEntity<Response> convert(@PathVariable UUID projectId,@PathVariable UUID deliverableId,
            @PathVariable UUID reviewId,@PathVariable UUID commentId,@RequestBody Request request,@AuthenticationPrincipal User user) {
        Response response=service.convert(projectId,deliverableId,reviewId,commentId,request,user);
        return ResponseEntity.status(response.created()?HttpStatus.CREATED:HttpStatus.OK).body(response);
    }
}
