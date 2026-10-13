package com.blackbox.agent.skill;

import com.blackbox.agent.proposal.TasksKind;
import com.blackbox.agent.tool.AgentToolbox;
import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Order(3)
@RequiredArgsConstructor
public class StatusBriefSkill implements AgentSkill {
    public static final String NAME = "STATUS_BRIEF";

    private final AgentToolbox toolbox;

    @Override public String name() { return NAME; }
    @Override public String label() { return "지금 상태 살펴보기"; }
    @Override public boolean contributorsOnly() { return false; }
    @Override public List<String> tools() { return toolbox.names(); }

    @Override public List<String> proposalKinds() { return List.of(TasksKind.NAME); }

    @Override public String material(UUID projectId, User user, JsonNode input) {
        return "지금 프로젝트의 상태를 살펴본다.";
    }
}
