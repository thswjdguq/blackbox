package com.blackbox.agent.skill;

import com.blackbox.dto.DeliverableDtos;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 확정·제출된 제출물은 모델을 부르기 전에 막는다. 채택하면 어차피 409라 기다린 시간과 호출만 버린다. */
class BreakDownTasksSkillTest {
    final UUID project = UUID.randomUUID(), deliverable = UUID.randomUUID();
    final DeliverableService deliverables = mock(DeliverableService.class);
    final BreakDownTasksSkill skill = new BreakDownTasksSkill(deliverables);

    @Test void aConfirmedOrSubmittedDeliverableIsRefusedBeforeTheModel() throws Exception {
        for (String status : List.of("CONFIRMED", "SUBMITTED")) {
            when(deliverables.get(any(), any(), any())).thenReturn(response(status));
            ResponseStatusException refused = assertThrows(ResponseStatusException.class, () -> skill.material(project, new User(), input()), status);
            assertEquals(400, refused.getStatusCode().value());
            assertEquals("확정된 제출물에는 업무를 더할 수 없습니다", refused.getReason());
        }
    }

    @Test void anOpenDeliverableIsGivenToTheModel() throws Exception {
        for (String status : List.of("DRAFT", "IN_REVIEW")) {
            when(deliverables.get(any(), any(), any())).thenReturn(response(status));
            assertTrue(skill.material(project, new User(), input()).contains("중간 보고서"), status);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode input() throws Exception {
        return new ObjectMapper().readTree("{\"deliverableId\":\"" + deliverable + "\"}");
    }

    private DeliverableDtos.Response response(String status) {
        return new DeliverableDtos.Response(deliverable, project, "중간 보고서", null, LocalDate.of(2026, 10, 18),
                null, null, null, List.of(), status, null);
    }
}
