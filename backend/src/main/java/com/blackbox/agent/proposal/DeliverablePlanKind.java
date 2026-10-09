package com.blackbox.agent.proposal;

import com.blackbox.dto.CreateTaskRequest;
import com.blackbox.dto.DeliverableDtos;
import com.blackbox.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 제출물 하나와 그 요구사항, 업무. 업무는 같은 카드의 요구사항을 key로 가리킨다. 담당자는 없다. */
@Component
@RequiredArgsConstructor
public class DeliverablePlanKind implements ProposalKind {
    public static final String NAME = "DELIVERABLE_PLAN";

    private final ObjectMapper json;
    private final Validator validator;

    @Override public String name() { return NAME; }

    @Override public Checked check(UUID projectId, User user, JsonNode content) {
        Content read;
        try {
            read = json.treeToValue(content, Content.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Checked.unreadable(content);
        }
        if (read.deliverable() == null) return new Checked(content, List.of("deliverable: 없다"));
        Content c = new Content(read.deliverable(),
                read.requirements() == null ? List.of() : read.requirements(), read.tasks() == null ? List.of() : read.tasks());

        Deliverable d = c.deliverable();
        // 안내문에 기한이 없으면 모델이 비워 두고 사람이 채운다. 채택할 때 다시 본다
        List<String> problems = new ArrayList<>(ProposalKind.violations(validator, "deliverable",
                new DeliverableDtos.SaveRequest(d.title(), d.description(), d.dueDate(), d.submissionMethod(), null), "dueDate"));
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < c.requirements().size(); i++) {
            Requirement r = c.requirements().get(i);
            String at = "requirements[" + i + "]";
            if (r == null) { problems.add(at + ": 비어 있다"); continue; }
            if (r.key() == null || r.key().isBlank() || !keys.add(r.key())) problems.add(at + ".key: 비었거나 겹친다");
            problems.addAll(ProposalKind.violations(validator, at, new DeliverableDtos.RequirementRequest(r.content(), r.required())));
        }
        for (int i = 0; i < c.tasks().size(); i++) {
            Task t = c.tasks().get(i);
            String at = "tasks[" + i + "]";
            if (t == null) { problems.add(at + ": 비어 있다"); continue; }
            problems.addAll(ProposalKind.violations(validator, at, t.toRequest(null, null)));
            if (t.requirementKey() != null && !keys.contains(t.requirementKey())) {
                problems.add(at + ".requirementKey: 같은 카드의 requirements에 없는 key다");
            }
        }
        return new Checked(json.valueToTree(c), problems);
    }

    record Content(Deliverable deliverable, List<Requirement> requirements, List<Task> tasks) {}
    record Deliverable(String title, String description, LocalDate dueDate, String submissionMethod) {}
    record Requirement(String key, String content, boolean required) {}
    record Task(String title, String completionCriteria, LocalDate dueDate, String requirementKey) {
        CreateTaskRequest toRequest(UUID deliverableId, UUID requirementId) {
            return new CreateTaskRequest(title, null, null, null, dueDate, null, null, deliverableId, requirementId, completionCriteria);
        }
    }
}
