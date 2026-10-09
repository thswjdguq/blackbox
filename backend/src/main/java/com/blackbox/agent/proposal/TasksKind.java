package com.blackbox.agent.proposal;

import com.blackbox.dto.CreateTaskRequest;
import com.blackbox.dto.DeliverableDtos.RequirementResponse;
import com.blackbox.entity.User;
import com.blackbox.exception.NotFoundException;
import com.blackbox.service.DeliverableService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 이미 있는 제출물에 붙일 업무 초안. 업무는 그 제출물의 요구사항을 id로 가리킨다. 담당자는 없다. */
@Component
@RequiredArgsConstructor
public class TasksKind implements ProposalKind {
    public static final String NAME = "TASKS";

    private final ObjectMapper json;
    private final Validator validator;
    private final DeliverableService deliverables;

    @Override public String name() { return NAME; }

    @Override public Checked check(UUID projectId, User user, JsonNode content) {
        Content c;
        try {
            c = json.treeToValue(content, Content.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Checked.unreadable(content);
        }
        if (c.tasks() == null || c.tasks().isEmpty()) return new Checked(content, List.of("tasks: 비어 있다"));

        List<String> problems = new ArrayList<>();
        Set<UUID> requirementIds = Set.of();
        if (c.deliverableId() != null) {
            try {
                requirementIds = deliverables.get(projectId, c.deliverableId(), user).requirements().stream()
                        .map(RequirementResponse::id).collect(Collectors.toSet());
            } catch (NotFoundException e) {
                problems.add("deliverableId: 이 프로젝트의 제출물이 아니다");
            }
        }
        for (int i = 0; i < c.tasks().size(); i++) {
            Task t = c.tasks().get(i);
            String at = "tasks[" + i + "]";
            if (t == null) { problems.add(at + ": 비어 있다"); continue; }
            problems.addAll(ProposalKind.violations(validator, at, t.toRequest(c.deliverableId())));
            if (t.requirementId() != null && !requirementIds.contains(t.requirementId())) {
                problems.add(at + ".requirementId: 그 제출물의 요구사항이 아니다");
            }
        }
        return new Checked(json.valueToTree(c), problems);
    }

    record Content(UUID deliverableId, List<Task> tasks) {}
    record Task(String title, String completionCriteria, LocalDate dueDate, UUID requirementId) {
        CreateTaskRequest toRequest(UUID deliverableId) {
            return new CreateTaskRequest(title, null, null, null, dueDate, null, null, deliverableId, requirementId, completionCriteria);
        }
    }
}
