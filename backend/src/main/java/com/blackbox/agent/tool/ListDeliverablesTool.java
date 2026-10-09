package com.blackbox.agent.tool;

import com.blackbox.dto.DeliverableDtos;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ListDeliverablesTool implements ProjectTool {
    public static final String NAME = "list_deliverables";

    private final DeliverableService deliverables;

    @Override public String name() { return NAME; }
    @Override public String label() { return "제출물과 요구사항 조회"; }
    @Override public String description() {
        return "제출물을 기한이 빠른 순으로 돌려준다. 제출물마다 요구사항과, 요구사항이 충족됐다고 확인됐는지(met)가 들어 있다.";
    }

    @Override public Object read(UUID projectId, User user, JsonNode arguments) {
        return deliverables.list(projectId, user).stream().map(Item::from).toList();
    }

    record Item(UUID id, String title, String description, LocalDate dueDate, String submissionMethod,
                String ownerName, List<Requirement> requirements) {
        static Item from(DeliverableDtos.Response d) {
            return new Item(d.id(), d.title(), d.description(), d.dueDate(), d.submissionMethod(), d.ownerName(),
                    d.requirements().stream()
                            .map(r -> new Requirement(r.id(), r.content(), r.required(), r.assessment() != null)).toList());
        }
    }
    record Requirement(UUID id, String content, boolean required, boolean met) {}
}
