package com.blackbox.agent;

import com.blackbox.dto.DeliverableDtos;
import com.blackbox.dto.TaskResponse;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DeliverableStageTest {
    private final UUID id = UUID.randomUUID();

    @Test void reviewAndFinalStatesTakePrecedenceOverUnfinishedWork() {
        TaskResponse task = mock(TaskResponse.class);
        when(task.deliverableId()).thenReturn(id);
        when(task.status()).thenReturn("TODO");
        UUID requirement = UUID.randomUUID();
        when(task.requirementId()).thenReturn(requirement);
        var requirements = List.of(new DeliverableDtos.RequirementResponse(requirement, "Required result", true, null));
        String[][] cases = {
            {"IN_REVIEW", null, "IN_REVIEW"},
            {"IN_REVIEW", "APPROVED", "APPROVED"},
            {"DRAFT", "CHANGES_REQUESTED", "CHANGES_REQUESTED"},
            {"CONFIRMED", "APPROVED", "CONFIRMED"},
            {"SUBMITTED", "APPROVED", "SUBMITTED"},
            {"DRAFT", null, "TASKS_IN_PROGRESS"},   // 검토 전이면 예전처럼 업무로
            {"DRAFT", "APPROVED", "TASKS_IN_PROGRESS"}   // 확정을 취소하면 검토 전 판정으로 돌아간다(K-20 0.3, K-30 0.8)
        };
        for (String[] c : cases) {
            assertEquals(DeliverableStage.valueOf(c[2]), DeliverableStage.of(response(requirements, c[0], c[1]), List.of(task)), c[0]);
        }
    }

    @Test void aSubmittedDeliverableWithoutRequirementsRemainsSubmitted() {
        assertEquals(DeliverableStage.SUBMITTED, DeliverableStage.of(response(List.of(), "SUBMITTED", null), List.of()));
        assertEquals(DeliverableStage.NO_REQUIREMENT, DeliverableStage.of(response(List.of(), "DRAFT", null), List.of()));
    }

    private DeliverableDtos.Response response(List<DeliverableDtos.RequirementResponse> requirements, String status, String decision) {
        return new DeliverableDtos.Response(id, UUID.randomUUID(), "Report", null, LocalDate.of(2026, 10, 18),
                null, null, null, requirements, status, decision);
    }
}
