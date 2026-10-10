package com.blackbox.agent;

import com.blackbox.dto.DeliverableDtos;
import com.blackbox.dto.TaskResponse;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableService;
import com.blackbox.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** 프로젝트의 제출물을 기한이 빠른 순으로 읽어 단계를 붙인다. 요청한 사람의 권한으로 기존 조회를 부른다. */
@Component
@RequiredArgsConstructor
public class DeliverableStages {
    private final DeliverableService deliverables;
    private final TaskService tasks;

    public List<Staged> read(UUID projectId, User user) {
        List<TaskResponse> projectTasks = tasks.listTasks(projectId, null, user);
        return deliverables.list(projectId, user).stream()
                .map(d -> new Staged(d, DeliverableStage.of(d, projectTasks))).toList();
    }

    public record Staged(DeliverableDtos.Response deliverable, DeliverableStage stage) {}
}
