package com.blackbox.agent.tool;

import com.blackbox.agent.AgentProperties;
import com.blackbox.agent.TaskStatus;
import com.blackbox.dto.TaskResponse;
import com.blackbox.entity.User;
import com.blackbox.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ListTasksTool implements ProjectTool {
    public static final String NAME = "list_tasks";

    private final TaskService tasks;
    private final AgentProperties properties;

    @Override public String name() { return NAME; }
    @Override public String label() { return "업무 조회"; }
    @Override public String description() {
        return "업무를 최근에 만든 순으로 돌려준다. 상태, 기한, 완료 시각, 담당자 이름, 연결된 제출물과 요구사항, 완료 기준이 들어 있다. "
                + "overdue는 끝나지 않았는데 기한이 지난 업무다.";
    }

    @Override public Object read(UUID projectId, User user, JsonNode arguments) {
        LocalDate today = properties.today();
        return tasks.listTasks(projectId, null, user).stream().map(t -> Item.from(t, today)).toList();
    }

    record Item(UUID id, String title, String status, LocalDate dueDate, OffsetDateTime completedAt, List<String> assignees,
                UUID deliverableId, String deliverableTitle, UUID requirementId, String requirementContent,
                String completionCriteria, boolean overdue) {
        static Item from(TaskResponse t, LocalDate today) {
            boolean overdue = !TaskStatus.isDone(t) && t.dueDate() != null && t.dueDate().isBefore(today);
            return new Item(t.id(), t.title(), t.status(), t.dueDate(), t.completedAt(),
                    t.assignees().stream().map(TaskResponse.AssigneeSummary::name).toList(),
                    t.deliverableId(), t.deliverableTitle(), t.requirementId(), t.requirementContent(),
                    t.completionCriteria(), overdue);
        }
    }
}
