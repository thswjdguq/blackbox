package com.blackbox.agent.proposal;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.proposal.RequirementEvidence.Quote;
import com.blackbox.agent.proposal.RequirementEvidence.Sentence;
import com.blackbox.dto.DeliverableDtos.RequirementRequest;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 빠진 요구사항(K-31 5.1, 7장). 채택하면 그 제출물에 요구사항이 더해진다 */
@Component
@RequiredArgsConstructor
public class RequirementAddKind implements ProposalKind {
    public static final String NAME = "REQUIREMENT_ADD";

    private final ObjectMapper json;
    private final Validator validator;
    private final DeliverableService deliverables;

    @Override public String name() { return NAME; }

    @Override public Checked check(UUID projectId, User user, JsonNode content, boolean forAccept) {
        Content c;
        try {
            c = json.treeToValue(content, Content.class);
        } catch (JacksonException | IllegalArgumentException e) {
            return Checked.unreadable(content);
        }
        List<String> problems = new ArrayList<>();
        if (c.requirement() == null) problems.add("requirement: 없다");
        else problems.addAll(ProposalKind.violations(validator, "requirement", c.requirement().toRequest()));
        if (c.reason() == null || c.reason().text() == null || c.reason().text().isBlank()) problems.add("reason.text: 비어 있다");
        return new Checked(json.valueToTree(c), problems);
    }

    /** 확정된 제출물이면 요구사항 저장이 409다(기존 잠금) */
    @Override public List<AgentProposal.Result> accept(UUID projectId, User user, JsonNode content) {
        Content c = json.convertValue(content, Content.class);
        UUID id = deliverables.saveRequirement(projectId, c.deliverableId(), null, c.requirement().toRequest(), user).id();
        return List.of(new AgentProposal.Result(TargetTypes.REQUIREMENT, id));
    }

    @Override public Set<UUID> briefIds(JsonNode content) { return RequirementEvidence.briefIds(content); }

    /** 사람이 고칠 수 있는 것은 요구사항의 문구와 필수 여부뿐이다(7장) */
    @Override public JsonNode keepFixed(JsonNode before, JsonNode edited) {
        ObjectNode kept = (ObjectNode) before.deepCopy();
        RequirementEvidence.take(kept, edited, "requirement", "content");
        RequirementEvidence.take(kept, edited, "requirement", "required");
        return kept;
    }

    record Content(String kind, UUID deliverableId, NewRequirement requirement, Sentence reason, Map<String, Quote> evidenceText) {}
    record NewRequirement(String content, boolean required, List<String> evidence) {
        RequirementRequest toRequest() { return new RequirementRequest(content, required); }
    }
}
