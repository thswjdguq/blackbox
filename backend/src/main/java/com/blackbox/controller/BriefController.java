package com.blackbox.controller;

import com.blackbox.dto.BriefDtos.*;
import com.blackbox.dto.DeliverableDtos.BriefSummary;
import com.blackbox.entity.User;
import com.blackbox.service.BriefService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** 과제 안내문 공간과 제출물 연결(K-31 2·3장) */
@RestController @RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}")
public class BriefController {
    private final BriefService service;

    @GetMapping("/briefs") public List<Response> list(@PathVariable UUID projectId, @AuthenticationPrincipal User user) {
        return service.list(projectId, user);
    }

    @GetMapping("/briefs/{briefId}")
    public Response get(@PathVariable UUID projectId, @PathVariable UUID briefId, @AuthenticationPrincipal User user) {
        return service.get(projectId, briefId, user);
    }

    @PostMapping("/briefs") @ResponseStatus(HttpStatus.CREATED)
    public Response create(@PathVariable UUID projectId, @Valid @RequestBody SaveRequest req, @AuthenticationPrincipal User user) {
        return service.create(projectId, req, user);
    }

    @PutMapping("/briefs/{briefId}")
    public Response update(@PathVariable UUID projectId, @PathVariable UUID briefId, @Valid @RequestBody SaveRequest req,
            @AuthenticationPrincipal User user) {
        return service.update(projectId, briefId, req, user);
    }

    @DeleteMapping("/briefs/{briefId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID briefId, @AuthenticationPrincipal User user) {
        service.delete(projectId, briefId, user);
    }

    @PostMapping("/deliverables/{deliverableId}/briefs/{briefId}")
    public List<BriefSummary> link(@PathVariable UUID projectId, @PathVariable UUID deliverableId, @PathVariable UUID briefId,
            @AuthenticationPrincipal User user) {
        return service.link(projectId, deliverableId, briefId, user);
    }

    @DeleteMapping("/deliverables/{deliverableId}/briefs/{briefId}")
    public List<BriefSummary> unlink(@PathVariable UUID projectId, @PathVariable UUID deliverableId, @PathVariable UUID briefId,
            @AuthenticationPrincipal User user) {
        return service.unlink(projectId, deliverableId, briefId, user);
    }
}
