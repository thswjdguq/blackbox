package com.blackbox.agent.skill;

import com.blackbox.agent.proposal.DocumentOutlineKind;
import com.blackbox.agent.tool.GetProjectTool;
import com.blackbox.agent.tool.ListDeliverablesTool;
import com.blackbox.agent.tool.ListMeetingsTool;
import com.blackbox.agent.tool.ListTasksTool;
import com.blackbox.dto.DeliverableDtos;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableService;
import com.blackbox.service.DeliverableStatusReader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** 문서 목차 만들기(K-59 2장). 제출물 하나의 요구사항과 업무·회의 기록으로 목차 카드 한 장을 만든다 */
@Component
@Order(6)
@RequiredArgsConstructor
public class DraftOutlineSkill implements AgentSkill {
    public static final String NAME = "DRAFT_OUTLINE";
    private static final String DELIVERABLE_ID = "deliverableId";

    private final DeliverableService deliverables;

    @Override public String name() { return NAME; }
    @Override public String label() { return "문서 목차 만들기"; }
    @Override public List<String> tools() {
        return List.of(GetProjectTool.NAME, ListDeliverablesTool.NAME, ListTasksTool.NAME, ListMeetingsTool.NAME);
    }
    @Override public List<String> proposalKinds() { return List.of(DocumentOutlineKind.NAME); }

    @Override public String material(UUID projectId, User user, JsonNode input) {
        UUID id = AgentSkill.deliverableId(input);
        // 다른 프로젝트의 제출물이면 여기서 404가 된다
        DeliverableDtos.Response deliverable = deliverables.get(projectId, id, user);
        // 확정·제출된 제출물의 문서는 다시 쓰지 않는다(K-20 4장의 문장)
        if (DeliverableStatusReader.isConfirmed(deliverable.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DeliverableStatusReader.CONFIRMED_DETAIL);
        }
        return "목차를 만들 제출물: id " + id + ", 제목 \"" + deliverable.title().replaceAll("\\R", " ") + "\"";
    }

    /** 모델이 쓴 제출물 id는 믿지 않고 고른 제출물로 채운다 */
    @Override public String complete(String kind, ObjectNode content, JsonNode input) {
        content.put(DELIVERABLE_ID, input.path(DELIVERABLE_ID).asString());
        return null;
    }
}
