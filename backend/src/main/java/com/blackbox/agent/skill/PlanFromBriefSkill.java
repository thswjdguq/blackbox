package com.blackbox.agent.skill;

import com.blackbox.agent.proposal.DeliverablePlanKind;
import com.blackbox.agent.run.AgentEvents;
import com.blackbox.agent.tool.GetProjectTool;
import com.blackbox.agent.tool.ListDeliverablesTool;
import com.blackbox.entity.User;
import com.blackbox.service.BriefService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 과제 안내문으로 계획 만들기(K-30 2장, K-31 4장). 저장된 안내문(briefId)이나 붙여 넣은 글(briefText)을 받는다.
 * 붙여 넣은 글은 실행이 자리를 얻은 뒤 안내문 공간에 저장하고, 그 안내문으로 계획을 만든다
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class PlanFromBriefSkill implements AgentSkill {
    public static final String NAME = "PLAN_FROM_BRIEF";

    private final BriefService briefs;

    @Override public String name() { return NAME; }
    @Override public String label() { return "과제 안내문으로 계획 만들기"; }
    @Override public List<String> tools() { return List.of(GetProjectTool.NAME, ListDeliverablesTool.NAME); }
    @Override public List<String> proposalKinds() { return List.of(DeliverablePlanKind.NAME); }

    /** 실행 전에 입력을 확인한다. 저장은 하지 않는다. 모델이 읽는 글은 begin이 읽거나 저장한 본문이다 */
    @Override public String material(UUID projectId, User user, JsonNode input) {
        UUID briefId = briefId(input);
        return briefId != null ? briefs.findForPlan(projectId, briefId, user).body() : planInput(input).body();
    }

    /**
     * 안내문을 한 번 읽거나 저장해, 그 본문을 모델에 주고 그 안내문과 본문 해시를 카드에 남긴다.
     * 준비(material) 뒤에 안내문이 고쳐졌어도 모델이 읽은 본문과 카드의 근거가 같고, 모델이 답하는 사이에 고쳐지면 해시로 알 수 있다
     */
    @Override public Begun begin(UUID projectId, User user, JsonNode input, AgentEvents events) {
        UUID briefId = briefId(input);
        BriefService.Used used = briefId != null ? briefs.findForPlan(projectId, briefId, user)
                : briefs.useForPlan(projectId, planInput(input), user);
        events.brief(used.id(), used.title(), used.created());
        ObjectNode begun = ((ObjectNode) input).deepCopy();
        begun.put("briefId", used.id().toString());
        begun.put("briefBodyHash", used.bodyHash());
        return new Begun(begun, used.body());
    }

    /** 채택하면 만들어질 제출물에 이 안내문이 걸린다. briefBodyHash는 모델이 읽은 본문의 해시다. 모델이 쓴 값은 믿지 않고 서버가 채운다 */
    @Override public void complete(ObjectNode content, JsonNode input) {
        content.put("briefId", input.path("briefId").asString());
        content.put("briefBodyHash", input.path("briefBodyHash").asString());
    }

    private BriefService.PlanInput planInput(JsonNode input) {
        if (!input.hasNonNull("briefText")) throw AgentSkill.badInput("과제 안내문 입력이 필요합니다");
        return briefs.checkForPlan(input.path("briefTitle").asString(null), input.path("briefText").asString(""));
    }

    private static UUID briefId(JsonNode input) {
        if (!input.hasNonNull("briefId")) return null;
        if (input.hasNonNull("briefText")) throw AgentSkill.badInput("briefId와 briefText 중 하나만 보내 주세요");
        try {
            return UUID.fromString(input.path("briefId").asString(""));
        } catch (IllegalArgumentException e) {
            throw AgentSkill.badInput("briefId 형식이 올바르지 않습니다");
        }
    }
}
