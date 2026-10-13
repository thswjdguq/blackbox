package com.blackbox.agent.skill;

import com.blackbox.agent.AgentProperties;
import com.blackbox.agent.proposal.DeliverablePlanKind;
import com.blackbox.agent.tool.GetProjectTool;
import com.blackbox.agent.tool.ListDeliverablesTool;
import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Order(1)
@RequiredArgsConstructor
public class PlanFromBriefSkill implements AgentSkill {
    public static final String NAME = "PLAN_FROM_BRIEF";

    private final AgentProperties properties;

    @Override public String name() { return NAME; }
    @Override public String label() { return "과제 안내문으로 계획 만들기"; }
    @Override public List<String> tools() { return List.of(GetProjectTool.NAME, ListDeliverablesTool.NAME); }
    @Override public List<String> proposalKinds() { return List.of(DeliverablePlanKind.NAME); }

    @Override public String material(UUID projectId, User user, JsonNode input) {
        return AgentSkill.requiredText(input, "briefText", "과제 안내문", properties.getBriefMaxLength());
    }
}
