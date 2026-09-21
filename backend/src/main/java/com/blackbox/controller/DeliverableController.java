package com.blackbox.controller;

import com.blackbox.dto.DeliverableDtos.*;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/deliverables")
public class DeliverableController {
    private final DeliverableService service;
    @GetMapping public List<Response> list(@PathVariable UUID projectId, @AuthenticationPrincipal User user) {
        return service.list(projectId, user);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Response create(@PathVariable UUID projectId, @Valid @RequestBody SaveRequest req, @AuthenticationPrincipal User user) {
        return service.save(projectId, null, req, user);
    }
    @PutMapping("/{id}") public Response update(@PathVariable UUID projectId, @PathVariable UUID id,
            @Valid @RequestBody SaveRequest req, @AuthenticationPrincipal User user) {
        return service.save(projectId, id, req, user);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id, @AuthenticationPrincipal User user) {
        service.delete(projectId, id, user);
    }
    @PostMapping("/{id}/requirements") @ResponseStatus(HttpStatus.CREATED)
    public RequirementResponse addRequirement(@PathVariable UUID projectId, @PathVariable UUID id,
            @Valid @RequestBody RequirementRequest req, @AuthenticationPrincipal User user) {
        return service.saveRequirement(projectId, id, null, req, user);
    }
    @PutMapping("/{id}/requirements/{requirementId}")
    public RequirementResponse updateRequirement(@PathVariable UUID projectId, @PathVariable UUID id,
            @PathVariable UUID requirementId, @Valid @RequestBody RequirementRequest req, @AuthenticationPrincipal User user) {
        return service.saveRequirement(projectId, id, requirementId, req, user);
    }
    @DeleteMapping("/{id}/requirements/{requirementId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRequirement(@PathVariable UUID projectId, @PathVariable UUID id,
            @PathVariable UUID requirementId, @AuthenticationPrincipal User user) {
        service.deleteRequirement(projectId, id, requirementId, user);
    }
}
