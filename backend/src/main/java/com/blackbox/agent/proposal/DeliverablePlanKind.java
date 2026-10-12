package com.blackbox.agent.proposal;

import com.blackbox.agent.AgentProposal;
import com.blackbox.dto.CreateTaskRequest;
import com.blackbox.dto.DeliverableDtos;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableService;
import com.blackbox.service.TaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 제출물 하나와 그 요구사항, 업무. 업무는 같은 카드의 요구사항을 key로 가리킨다. 담당자는 없다. */
@Component
@RequiredArgsConstructor
public class DeliverablePlanKind implements ProposalKind {
    public static final String NAME = "DELIVERABLE_PLAN";

    private final ObjectMapper json;
    private final Validator validator;
    private final DeliverableService deliverables;
    private final TaskService tasks;

    @Override public String name() { return NAME; }

    @Override public Checked check(UUID projectId, User user, JsonNode content, boolean forAccept) {
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
        // 안내문에 기한이 없으면 모델이 비워 두고 사람이 채운다. 채택할 때는 있어야 한다
        List<String> problems = new ArrayList<>(ProposalKind.violations(validator, "deliverable",
                d.toRequest(), forAccept ? new String[0] : new String[] {"dueDate"}));
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

    @Override public List<AgentProposal.Result> accept(UUID projectId, User user, JsonNode content) {
        Content c = json.convertValue(content, Content.class);
        List<AgentProposal.Result> created = new ArrayList<>();
        UUID deliverableId = deliverables.save(projectId, null, c.deliverable().toRequest(), user).id();
        created.add(new AgentProposal.Result(TargetTypes.DELIVERABLE, deliverableId));
        Map<String, UUID> requirementIds = new HashMap<>();
        for (Requirement r : c.requirements()) {
            UUID id = deliverables.saveRequirement(projectId, deliverableId, null,
                    new DeliverableDtos.RequirementRequest(r.content(), r.required()), user).id();
            requirementIds.put(r.key(), id);
            created.add(new AgentProposal.Result(TargetTypes.REQUIREMENT, id));
        }
        for (Task t : c.tasks()) {
            UUID id = tasks.createTask(projectId, t.toRequest(deliverableId, requirementIds.get(t.requirementKey())), user).id();
            created.add(new AgentProposal.Result(TargetTypes.TASK, id));
        }
        return created;
    }

    record Content(Deliverable deliverable, List<Requirement> requirements, List<Task> tasks) {}
    record Deliverable(String title, String description, LocalDate dueDate, String submissionMethod) {
        DeliverableDtos.SaveRequest toRequest() {
            return new DeliverableDtos.SaveRequest(title, description, dueDate, submissionMethod, null);
        }
    }
    record Requirement(String key, String content, boolean required) {}
    record Task(String title, String completionCriteria, LocalDate dueDate, String requirementKey) {
        CreateTaskRequest toRequest(UUID deliverableId, UUID requirementId) {
            return new CreateTaskRequest(title, null, null, null, dueDate, null, null, deliverableId, requirementId, completionCriteria);
        }
    }
}
