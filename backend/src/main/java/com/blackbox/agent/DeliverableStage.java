package com.blackbox.agent;

import com.blackbox.dto.DeliverableDtos;
import com.blackbox.dto.DeliverableDtos.RequirementResponse;
import com.blackbox.dto.TaskResponse;

import java.util.List;

/**
 * 제출물이 지금 어느 단계인지(K-30 3장). 프로젝트 홈의 "다음 할 일" 카드(frontend NextStepCard)와 같은 순서로 판정한다.
 * 위에서부터 먼저 맞는 것이 단계다. 단계를 더할 때는 값과 of의 한 줄을 더한다.
 */
public enum DeliverableStage {
    NO_REQUIREMENT("요구사항이 없다"),
    NO_TASK("연결된 업무가 없다"),
    UNCOVERED_REQUIREMENT("아직 확인 전인 필수 요구사항 중 업무가 없는 것이 있다"),
    TASKS_IN_PROGRESS("끝나지 않은 연결 업무가 있다"),
    UNCHECKED_REQUIREMENT("업무는 끝났고 확인하지 않은 필수 요구사항이 있다"),
    READY_FOR_REVIEW("업무와 필수 요구사항 확인이 끝났다");

    private final String meaning;

    DeliverableStage(String meaning) { this.meaning = meaning; }

    public String meaning() { return meaning; }

    /** projectTasks는 프로젝트의 업무 전부여도 된다. 이 제출물에 연결된 것만 본다 */
    public static DeliverableStage of(DeliverableDtos.Response deliverable, List<TaskResponse> projectTasks) {
        List<TaskResponse> linked = projectTasks.stream().filter(t -> deliverable.id().equals(t.deliverableId())).toList();
        List<RequirementResponse> required = deliverable.requirements().stream().filter(RequirementResponse::required).toList();
        List<RequirementResponse> unchecked = required.stream().filter(r -> r.assessment() == null).toList();
        boolean uncovered = unchecked.stream()
                .anyMatch(r -> linked.stream().noneMatch(t -> r.id().equals(t.requirementId())));

        if (deliverable.requirements().isEmpty()) return NO_REQUIREMENT;
        // 필수 요구사항이 모두 확인됐다면 업무 없이도 다음 단계로 간다
        if (linked.isEmpty() && (required.isEmpty() || !unchecked.isEmpty())) return NO_TASK;
        if (uncovered) return UNCOVERED_REQUIREMENT;
        if (linked.stream().anyMatch(t -> !TaskStatus.isDone(t))) return TASKS_IN_PROGRESS;
        if (!unchecked.isEmpty()) return UNCHECKED_REQUIREMENT;
        return READY_FOR_REVIEW;
    }
}
