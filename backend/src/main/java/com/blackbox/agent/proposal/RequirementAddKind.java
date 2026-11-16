package com.blackbox.agent.proposal;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.proposal.RequirementEvidence.Quote;
import com.blackbox.agent.proposal.RequirementEvidence.Sentence;
import com.blackbox.dto.DeliverableDtos.RequirementRequest;
import com.blackbox.entity.Deliverable;
import com.blackbox.entity.ProjectBrief;
import com.blackbox.entity.User;
import com.blackbox.exception.CodedException;
import com.blackbox.repository.DeliverableRequirementRepository;
import com.blackbox.service.DeliverableService;
import com.blackbox.service.DeliverableStatuses;
import com.blackbox.service.ProjectAccessChecker;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final DeliverableStatuses statuses;
    private final ProjectAccessChecker access;
    private final DeliverableRequirementRepository requirements;

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

    /**
     * 제출물을 잠근 뒤(확정이면 409) 같은 문구의 요구사항이 있는지 본다(K-31 5.1). 카드가 대기하는 사이 사람이 직접 등록했거나
     * 같은 지적의 다른 카드를 먼저 채택했으면 409 REQUIREMENT_EXISTS다. 요구사항 저장도 이 잠금을 거치므로 확인과 저장 사이에 같은 요구사항이 생기지 않는다
     */
    @Override public List<AgentProposal.Result> accept(UUID projectId, User user, JsonNode content) {
        Content c = json.convertValue(content, Content.class);
        Deliverable d = statuses.lockEditable(access.getProject(projectId), c.deliverableId());
        String wanted = key(c.requirement().content());
        if (requirements.findByDeliverableOrderByCreatedAtAsc(d).stream().anyMatch(r -> key(r.getContent()).equals(wanted))) {
            throw new CodedException(HttpStatus.CONFLICT, "REQUIREMENT_EXISTS",
                    "같은 요구사항이 이미 등록되어 있습니다. 이 제안은 거절로 닫아 주세요", Map.of());
        }
        UUID id = deliverables.saveRequirement(projectId, d.getId(), null, c.requirement().toRequest(), user).id();
        return List.of(new AgentProposal.Result(TargetTypes.REQUIREMENT, id));
    }

    @Override public Set<UUID> briefIds(JsonNode content) { return RequirementEvidence.briefIds(content); }

    /** 같은 요구사항인지 볼 때 앞뒤 공백과 대소문자를 가리지 않는다(K-31 5장). 찾을 때와 채택할 때 같은 기준이다 */
    public static String key(String text) { return ProjectBrief.normalize(text).toLowerCase(Locale.ROOT); }

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
