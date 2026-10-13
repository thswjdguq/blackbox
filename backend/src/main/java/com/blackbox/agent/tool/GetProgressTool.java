package com.blackbox.agent.tool;

import com.blackbox.entity.User;
import com.blackbox.service.DeliverableProgressService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetProgressTool implements ProjectTool {
    public static final String NAME = "get_progress";
    private static final String DELIVERABLE_ID = "deliverableId";

    private final DeliverableProgressService progress;

    @Override public String name() { return NAME; }
    @Override public String label() { return "진척 조회"; }
    @Override public String description() { return "제출물 하나의 업무 완료율과 필수 요구사항 충족률을 돌려준다."; }
    @Override public String inputSchema() {
        return """
                {"type":"object","properties":{"%s":{"type":"string","description":"제출물의 id. list_deliverables가 돌려준 값"}},"required":["%s"]}"""
                .formatted(DELIVERABLE_ID, DELIVERABLE_ID);
    }

    @Override public Object read(UUID projectId, User user, JsonNode arguments) {
        String id = arguments.path(DELIVERABLE_ID).asText("");
        if (id.isBlank()) throw new BadArguments(DELIVERABLE_ID + "가 필요합니다");
        UUID deliverableId;
        try {
            deliverableId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadArguments(DELIVERABLE_ID + "의 형식이 올바르지 않습니다");
        }
        return progress.get(projectId, deliverableId, user);
    }
}
