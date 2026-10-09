package com.blackbox.agent.skill;

import com.blackbox.agent.AgentProperties;
import com.blackbox.agent.tool.AgentToolbox;
import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Order(4)
@RequiredArgsConstructor
public class AskSkill implements AgentSkill {
    public static final String NAME = "ASK";

    private final AgentToolbox toolbox;
    private final AgentProperties properties;

    @Override public String name() { return NAME; }
    @Override public String label() { return "물어보기"; }
    @Override public boolean contributorsOnly() { return false; }
    @Override public List<String> tools() { return toolbox.names(); }

    @Override public String material(UUID projectId, User user, JsonNode input) {
        return AgentSkill.requiredText(input, "question", "질문", properties.getQuestionMaxLength());
    }
}
