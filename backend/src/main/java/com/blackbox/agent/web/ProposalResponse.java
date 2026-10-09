package com.blackbox.agent.web;

import com.blackbox.agent.AgentProposal;
import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 제안 카드의 응답(K-30 3장). decision과 results는 없을 때도 null로 내보내고 생략하지 않는다. */
public record ProposalResponse(UUID id, UUID runId, String kind, String status, String title, String rationale,
                               JsonNode content, boolean edited, Person requestedBy, OffsetDateTime createdAt,
                               Decision decision, Results results) {
    public record Person(UUID userId, String name) {
        static Person of(User user) { return new Person(user.getId(), user.getName()); }
    }
    public record Decision(String result, Person decidedBy, OffsetDateTime decidedAt) {}
    public record Results(UUID deliverableId, List<UUID> requirementIds, List<UUID> taskIds) {}

    public static ProposalResponse from(AgentProposal p) {
        Decision decision = p.isPending() ? null
                : new Decision(p.getStatus().name(), Person.of(p.getDecidedBy()), p.getDecidedAt());
        return new ProposalResponse(p.getId(), p.getRun().getId(), p.getKind(), p.getStatus().name(), p.getTitle(),
                p.getRationale(), p.getContent(), p.isEdited(), Person.of(p.getRun().getRequestedBy()), p.getCreatedAt(),
                decision, null);
    }
}
