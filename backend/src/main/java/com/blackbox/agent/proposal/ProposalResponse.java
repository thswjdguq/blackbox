package com.blackbox.agent.proposal;

import com.blackbox.agent.AgentProposal;
import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 제안 카드의 응답(K-30 3장). decision과 results는 없을 때도 null로 내보내고 생략하지 않는다.
 * 지연 로딩되는 것을 읽으므로 트랜잭션 안에서 만든다.
 */
public record ProposalResponse(UUID id, UUID runId, String kind, String status, String title, String rationale,
                               JsonNode content, boolean edited, Person requestedBy, OffsetDateTime createdAt,
                               Decision decision, Results results) {
    public record Person(UUID userId, String name) {
        static Person of(User user) { return new Person(user.getId(), user.getName()); }
    }
    public record Decision(String result, Person decidedBy, OffsetDateTime decidedAt) {}
    /** 채택으로 만들어진 것의 id. 계약이 정한 모양이라 대상 종류 셋을 칸으로 나눈다. 순서는 조회할 때마다 같게만 하고 뜻은 없다 */
    public record Results(UUID deliverableId, List<UUID> requirementIds, List<UUID> taskIds) {
        static Results of(Collection<AgentProposal.Result> created) {
            return new Results(ids(created, TargetTypes.DELIVERABLE).stream().findFirst().orElse(null),
                    ids(created, TargetTypes.REQUIREMENT), ids(created, TargetTypes.TASK));
        }
        private static List<UUID> ids(Collection<AgentProposal.Result> created, String type) {
            return created.stream().filter(r -> r.targetType().equals(type)).map(AgentProposal.Result::targetId).sorted().toList();
        }
    }

    public static ProposalResponse from(AgentProposal p) {
        Decision decision = p.isPending() ? null
                : new Decision(p.getStatus().name(), Person.of(p.getDecidedBy()), p.getDecidedAt());
        return new ProposalResponse(p.getId(), p.getRun().getId(), p.getKind(), p.getStatus().name(), p.getTitle(),
                p.getRationale(), p.getContent(), p.isEdited(), Person.of(p.getRun().getRequestedBy()), p.getCreatedAt(),
                decision, p.getStatus() == AgentProposal.Status.ACCEPTED ? Results.of(p.getResults()) : null);
    }
}
