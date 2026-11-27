package com.blackbox.agent.skill;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.AgentProposalRepository;
import com.blackbox.agent.proposal.DocumentDraftKind;
import com.blackbox.agent.proposal.DocumentOutlineKind;
import com.blackbox.agent.run.AgentEvents;
import com.blackbox.agent.tool.GetProjectTool;
import com.blackbox.agent.tool.ListDeliverablesTool;
import com.blackbox.agent.tool.ListMeetingsTool;
import com.blackbox.agent.tool.ListTasksTool;
import com.blackbox.dto.DeliverableDtos;
import com.blackbox.entity.User;
import com.blackbox.exception.CodedException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.service.DeliverableService;
import com.blackbox.service.DeliverableStatusReader;
import com.blackbox.service.ProjectAccessChecker;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 문서 초안 쓰기(K-59 2장). 채택한 목차 카드대로 장별 초안 카드 한 장을 만든다.
 * 근거로 쓸 수 있는 id는 모델을 부르기 전에 한 번 읽어 두고, 모델이 쓴 근거 중 그 밖의 것은 빼고 개수를 글 끝에 알린다
 */
@Component
@Order(7)
@RequiredArgsConstructor
public class DraftDocumentSkill implements AgentSkill {
    public static final String NAME = "DRAFT_DOCUMENT";
    public static final String OUTLINE_NOT_ACCEPTED = "채택한 목차로만 초안을 쓸 수 있습니다. 목차 카드를 먼저 채택해 주세요";
    static final String DROPPED_NOTE = "근거를 확인할 수 없어 %d건은 뺐습니다";
    private static final String OUTLINE_ID = "outlineProposalId", DELIVERABLE_ID = "deliverableId", KNOWN = "known", DROPPED = "droppedSources";

    private final ProjectAccessChecker access;
    private final AgentProposalRepository proposals;
    private final DeliverableService deliverables;
    private final DocumentDraftKind drafts;
    private final ObjectMapper json;

    @Override public String name() { return NAME; }
    @Override public String label() { return "문서 초안 쓰기"; }
    @Override public List<String> tools() {
        return List.of(GetProjectTool.NAME, ListDeliverablesTool.NAME, ListTasksTool.NAME, ListMeetingsTool.NAME);
    }
    @Override public List<String> proposalKinds() { return List.of(DocumentDraftKind.NAME); }

    @Override public String material(UUID projectId, User user, JsonNode input) {
        Source s = source(projectId, user, input);
        Map<UUID, DeliverableDtos.RequirementResponse> requirements = s.deliverable().requirements().stream()
                .collect(Collectors.toMap(DeliverableDtos.RequirementResponse::id, r -> r));
        JsonNode outline = s.outline().getContent();
        StringBuilder text = new StringBuilder("초안을 쓸 제출물: id ").append(s.deliverable().id())
                .append(", 제목 \"").append(oneLine(s.deliverable().title())).append("\"")
                .append("\n문서 제목: ").append(oneLine(outline.path("title").asString("")))
                .append("\n\n채택한 목차");
        int no = 1;
        for (JsonNode section : outline.path("sections")) {
            text.append("\n\n").append(no++).append(". ").append(oneLine(section.path("title").asString("")));
            for (JsonNode id : section.path("requirementIds")) {
                DeliverableDtos.RequirementResponse r = requirements.get(uuid(id.asString("")));
                if (r != null) text.append("\n- 다룰 요구사항(").append(r.required() ? "필수" : "선택").append(", id ").append(r.id()).append("): ").append(oneLine(r.content()));
            }
            String note = section.path("note").asString("");
            if (!note.isBlank()) text.append("\n- 메모: ").append(oneLine(note));
        }
        return text.toString();
    }

    /** 모델을 부르기 전에 근거로 쓸 수 있는 id를 읽어 둔다. complete가 이것으로 모델이 쓴 근거를 거른다 */
    @Override public Begun begin(UUID projectId, User user, JsonNode input, AgentEvents events) {
        Source s = source(projectId, user, input);
        DocumentDraftKind.Known known = drafts.known(projectId, user, s.deliverable().id());
        ObjectNode begun = ((ObjectNode) input).deepCopy();
        begun.put(DELIVERABLE_ID, s.deliverable().id().toString());
        begun.set(KNOWN, json.valueToTree(known));
        return new Begun(begun, null);
    }

    /** 제출물과 목차는 모델이 쓴 값이 아니라 고른 것으로 채운다 */
    @Override public String complete(String kind, ObjectNode content, JsonNode input) {
        content.put(DELIVERABLE_ID, input.path(DELIVERABLE_ID).asString());
        content.put(OUTLINE_ID, input.path(OUTLINE_ID).asString());
        JsonNode k = input.path(KNOWN);
        int dropped = DocumentDraftKind.keepKnown(content, new DocumentDraftKind.Known(ids(k.path("taskIds")), ids(k.path("meetingIds")), ids(k.path("requirementIds"))));
        ((ObjectNode) input).put(DROPPED, input.path(DROPPED).asInt(0) + dropped);
        return null;
    }

    @Override public List<String> notes(JsonNode input) {
        int dropped = input.path(DROPPED).asInt(0);
        return dropped == 0 ? List.of() : List.of(DROPPED_NOTE.formatted(dropped));
    }

    private record Source(AgentProposal outline, DeliverableDtos.Response deliverable) {}

    /**
     * 목차 카드는 같은 프로젝트의 채택된 목차여야 한다(K-59 2장). 없거나 다른 프로젝트이거나 목차가 아니면 404, 채택 전이거나 거절됐으면 409다.
     * 목차의 제출물이 확정·제출됐으면 409다
     */
    private Source source(UUID projectId, User user, JsonNode input) {
        UUID id = uuid(input.path(OUTLINE_ID).asString(""));
        if (id == null) throw AgentSkill.badInput("목차를 골라주세요");
        AgentProposal outline = proposals.findByIdAndProject(id, access.getProject(projectId))
                .filter(p -> p.getKind().equals(DocumentOutlineKind.NAME))
                .orElseThrow(() -> new NotFoundException("목차 제안을 찾을 수 없습니다"));
        if (outline.getStatus() != AgentProposal.Status.ACCEPTED) {
            throw new CodedException(HttpStatus.CONFLICT, "OUTLINE_NOT_ACCEPTED", OUTLINE_NOT_ACCEPTED, Map.of());
        }
        // 목차를 만든 뒤 제출물이 지워졌으면 여기서 404가 된다
        DeliverableDtos.Response deliverable = deliverables.get(projectId, uuid(outline.getContent().path(DELIVERABLE_ID).asString("")), user);
        if (DeliverableStatusReader.isConfirmed(deliverable.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DeliverableStatusReader.CONFIRMED_DETAIL);
        }
        return new Source(outline, deliverable);
    }

    private static Set<UUID> ids(JsonNode array) {
        Set<UUID> out = new HashSet<>();
        for (JsonNode id : array) { UUID u = uuid(id.asString("")); if (u != null) out.add(u); }
        return out;
    }

    private static String oneLine(String s) { return s.replaceAll("\\R", " "); }

    private static UUID uuid(String s) {
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
