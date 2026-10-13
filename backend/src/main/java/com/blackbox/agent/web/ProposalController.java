package com.blackbox.agent.web;

import com.blackbox.agent.proposal.ProposalResponse;
import com.blackbox.agent.proposal.ProposalService;
import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** K-30 2장의 제안 카드 조회·수정·채택·거절. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/agent/proposals")
public class ProposalController {
    private final ProposalService service;

    @GetMapping
    public List<ProposalResponse> list(@PathVariable UUID projectId, @RequestParam(required = false) String status,
                                       @AuthenticationPrincipal User user) {
        return service.list(projectId, user, status);
    }

    @GetMapping("/{proposalId}")
    public ProposalResponse get(@PathVariable UUID projectId, @PathVariable UUID proposalId, @AuthenticationPrincipal User user) {
        return service.get(projectId, user, proposalId);
    }

    public record EditRequest(JsonNode content) {}

    @PutMapping("/{proposalId}")
    public ProposalResponse edit(@PathVariable UUID projectId, @PathVariable UUID proposalId, @RequestBody EditRequest request,
                                 @AuthenticationPrincipal User user) {
        return service.edit(projectId, user, proposalId, request.content());
    }

    @PostMapping("/{proposalId}/accept")
    public ProposalResponse accept(@PathVariable UUID projectId, @PathVariable UUID proposalId, @AuthenticationPrincipal User user) {
        return service.accept(projectId, user, proposalId);
    }

    @PostMapping("/{proposalId}/reject")
    public ProposalResponse reject(@PathVariable UUID projectId, @PathVariable UUID proposalId, @AuthenticationPrincipal User user) {
        return service.reject(projectId, user, proposalId);
    }
}
