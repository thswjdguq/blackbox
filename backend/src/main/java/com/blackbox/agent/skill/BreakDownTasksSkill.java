package com.blackbox.agent.skill;

import com.blackbox.agent.proposal.TasksKind;
import com.blackbox.agent.tool.GetProjectTool;
import com.blackbox.agent.tool.ListDeliverablesTool;
import com.blackbox.agent.tool.ListTasksTool;
import com.blackbox.dto.DeliverableDtos;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableService;
import com.blackbox.service.DeliverableStatusReader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Order(2)
@RequiredArgsConstructor
public class BreakDownTasksSkill implements AgentSkill {
    public static final String NAME = "BREAK_DOWN_TASKS";
    private static final String DELIVERABLE_ID = "deliverableId";

    private final DeliverableService deliverables;

    @Override public String name() { return NAME; }
    @Override public String label() { return "요구사항을 업무로 나누기"; }
    @Override public List<String> tools() { return List.of(GetProjectTool.NAME, ListDeliverablesTool.NAME, ListTasksTool.NAME); }
    @Override public List<String> proposalKinds() { return List.of(TasksKind.NAME); }

    @Override public String material(UUID projectId, User user, JsonNode input) {
        UUID id = AgentSkill.deliverableId(input);
        // 다른 프로젝트의 제출물이면 여기서 404가 된다
        DeliverableDtos.Response deliverable = deliverables.get(projectId, id, user);
        // 확정·제출된 제출물에는 업무를 더할 수 없어 채택이 409가 된다. 모델을 부르기 전에 막는다
        if (DeliverableStatusReader.isConfirmed(deliverable.status())) throw AgentSkill.badInput("확정된 제출물에는 업무를 더할 수 없습니다");
        return "업무로 나눌 제출물: id " + id + ", 제목 \"" + deliverable.title() + "\"";
    }

    @Override public String complete(String kind, ObjectNode content, JsonNode input) {
        content.put(DELIVERABLE_ID, input.path(DELIVERABLE_ID).asString());
        return null;
    }
}
