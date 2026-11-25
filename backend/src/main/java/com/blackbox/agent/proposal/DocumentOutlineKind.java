package com.blackbox.agent.proposal;

import com.blackbox.agent.AgentProposal;
import com.blackbox.dto.DeliverableDtos.RequirementResponse;
import com.blackbox.entity.User;
import com.blackbox.exception.NotFoundException;
import com.blackbox.service.DeliverableService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 문서 목차(K-59 3장). 채택해도 기록을 만들지 않는다. 채택은 "이 목차로 초안을 쓴다"는 확정이고, 채택한 카드만 초안 실행의 입력이 된다.
 * 어느 장에도 걸리지 않은 필수 요구사항(uncoveredRequirementIds)은 서버가 검증할 때마다 다시 계산한다. 채택을 막지는 않는다
 */
@Component
@RequiredArgsConstructor
public class DocumentOutlineKind implements ProposalKind {
    public static final String NAME = "DOCUMENT_OUTLINE";
    static final int MAX_SECTIONS = 6, TITLE_MAX = 255, SECTION_TITLE_MAX = 100, NOTE_MAX = 500;

    private final ObjectMapper json;
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
        List<RequirementResponse> requirements = List.of();
        if (c.deliverableId() == null) problems.add("deliverableId: 없다");
        else try {
            requirements = deliverables.get(projectId, c.deliverableId(), user).requirements();
        } catch (NotFoundException e) {
            problems.add("deliverableId: 이 프로젝트의 제출물이 아니다");
        }
        text(problems, "title", c.title(), TITLE_MAX, true);
        List<Section> sections = c.sections() == null ? List.of() : c.sections();
        if (sections.isEmpty() || sections.size() > MAX_SECTIONS) problems.add("sections: 1-" + MAX_SECTIONS + "개여야 한다");

        Set<UUID> known = new HashSet<>(), covered = new HashSet<>();
        requirements.forEach(r -> known.add(r.id()));
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            String at = "sections[" + i + "]";
            if (s == null) { problems.add(at + ": 비어 있다"); continue; }
            text(problems, at + ".title", s.title(), SECTION_TITLE_MAX, true);
            text(problems, at + ".note", s.note(), NOTE_MAX, false);
            for (UUID id : s.requirementIds()) {
                if (known.contains(id)) covered.add(id);
                else problems.add(at + ".requirementIds: " + id + "은 그 제출물의 요구사항이 아니다");
            }
        }
        List<UUID> uncovered = requirements.stream().filter(RequirementResponse::required).map(RequirementResponse::id)
                .filter(id -> !covered.contains(id)).toList();
        ObjectNode kept = json.valueToTree(c);
        kept.set("uncoveredRequirementIds", json.valueToTree(uncovered));
        return new Checked(kept, problems);
    }

    /** 기록을 만들지 않는다(K-59 3장). 채택 상태가 초안 실행의 입력이 된다 */
    @Override public List<AgentProposal.Result> accept(UUID projectId, User user, JsonNode content) { return List.of(); }

    /** 제출물은 바꿀 수 없다. 나머지는 사람이 고친다 */
    @Override public JsonNode keepFixed(JsonNode before, JsonNode edited) {
        ObjectNode kept = ((ObjectNode) edited).deepCopy();
        kept.set("deliverableId", before.get("deliverableId"));
        return kept;
    }

    private static void text(List<String> problems, String at, String value, int max, boolean required) {
        if (value == null || value.isBlank()) { if (required) problems.add(at + ": 비어 있다"); }
        else if (value.length() > max) problems.add(at + ": " + max + "자까지다");
    }

    record Content(UUID deliverableId, String title, List<Section> sections) {}
    record Section(String title, List<UUID> requirementIds, String note) {
        Section { requirementIds = requirementIds == null ? List.of() : requirementIds.stream().distinct().toList(); }
    }
}
