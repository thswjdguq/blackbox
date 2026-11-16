package com.blackbox.agent.proposal;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.proposal.RequirementEvidence.Quote;
import com.blackbox.agent.proposal.RequirementEvidence.RequirementQuote;
import com.blackbox.agent.proposal.RequirementEvidence.Sentence;
import com.blackbox.dto.DeliverableDtos.RequirementRequest;
import com.blackbox.dto.DeliverableDtos.RequirementResponse;
import com.blackbox.entity.Deliverable;
import com.blackbox.entity.DeliverableRequirement;
import com.blackbox.entity.User;
import com.blackbox.exception.CodedException;
import com.blackbox.exception.NotFoundException;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 모호한 요구사항(K-31 5.2, 7장). 채택하면 그 요구사항의 문구가 수정안으로 바뀐다.
 * 수정안이 없으면 확인 질문만 있는 카드이고, 채택할 수 없어 거절로 닫는다
 */
@Component
@RequiredArgsConstructor
public class RequirementReviseKind implements ProposalKind {
    public static final String NAME = "REQUIREMENT_REVISE";

    private final ObjectMapper json;
    private final Validator validator;
    private final DeliverableService deliverables;
    private final DeliverableStatuses statuses;
    private final ProjectAccessChecker access;

    @Override public String name() { return NAME; }

    @Override public Checked check(UUID projectId, User user, JsonNode content, boolean forAccept) {
        Content c;
        try {
            c = json.treeToValue(content, Content.class);
        } catch (JacksonException | IllegalArgumentException e) {
            return Checked.unreadable(content);
        }
        List<String> problems = new ArrayList<>();
        if (c.issue() == null || blank(c.issue().text())) problems.add("issue.text: 비어 있다");
        if (c.revision() != null) {
            problems.addAll(ProposalKind.violations(validator, "revision", new RequirementRequest(c.revision().content(), true)));
        } else if (blank(c.question())) {
            problems.add("question: 수정안이 없으면 확인 질문이 있어야 한다");
        }
        return new Checked(json.valueToTree(c), problems);
    }

    /**
     * 제출물을 잠근 뒤 요구사항이 카드를 만들 때와 같은지 본다. 같은 제출물의 요구사항 저장도 이 잠금을 거치므로,
     * 확인과 저장 사이에 다른 사람이 문구를 바꾸지 못한다. 문구가 달라지면 충족 확인이 풀린다(기존 규칙)
     */
    @Override public List<AgentProposal.Result> accept(UUID projectId, User user, JsonNode content) {
        Content c = json.convertValue(content, Content.class);
        if (c.revision() == null) {
            throw new CodedException(HttpStatus.CONFLICT, "QUESTION_ONLY",
                    "확인 질문만 있는 제안은 채택할 수 없습니다. 확인한 뒤 거절로 닫아 주세요", Map.of());
        }
        Deliverable d = statuses.lockEditable(access.getProject(projectId), c.deliverableId());
        DeliverableRequirement r = current(d, c.requirementId());
        if (r == null || !r.getContent().equals(c.currentContent())) {
            throw new CodedException(HttpStatus.CONFLICT, "REQUIREMENT_CHANGED",
                    "제안을 만든 뒤 요구사항이 바뀌었거나 지워졌습니다. 요구사항 찾기를 다시 실행해 주세요", Map.of());
        }
        boolean assessed = r.getAssessedAt() != null;
        RequirementResponse saved = deliverables.saveRequirement(projectId, d.getId(), r.getId(),
                new RequirementRequest(c.revision().content(), r.isRequired()), user);
        return assessed && saved.assessment() == null
                ? List.of(new AgentProposal.Result(TargetTypes.ASSESSMENT_CLEARED, r.getId())) : List.of();
    }

    @Override public Set<UUID> briefIds(JsonNode content) { return RequirementEvidence.briefIds(content); }

    /** 사람이 고칠 수 있는 것은 수정안의 문구와 확인 질문뿐이다(7장). 질문만 있는 카드에 수정안을 더할 수는 없다 */
    @Override public JsonNode keepFixed(JsonNode before, JsonNode edited) {
        ObjectNode kept = (ObjectNode) before.deepCopy();
        RequirementEvidence.take(kept, edited, "revision", "content");
        if (edited.has("question")) kept.set("question", edited.get("question"));
        return kept;
    }

    private DeliverableRequirement current(Deliverable d, UUID requirementId) {
        try {
            return deliverables.findRequirement(d, requirementId);
        } catch (NotFoundException e) {
            return null;
        }
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    record Content(String kind, UUID deliverableId, UUID requirementId, String currentContent, Sentence issue, Revision revision,
                   String question, Map<String, Quote> evidenceText, Map<String, RequirementQuote> evidenceRequirements) {}
    record Revision(String content, List<String> evidence) {}
}
