package com.blackbox.agent;

import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 실행과 제안 카드의 상태가 정해진 길로만 바뀌는지 본다. DB 없이 돈다. */
class AgentStateTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-10T14:00:00+09:00");
    private final ObjectMapper json = new ObjectMapper();
    private final User user = new User();

    @Test void decidedProposalCannotBeEditedOrDecidedAgain() {
        AgentProposal accepted = proposal(); accepted.accept(user, List.of(new AgentProposal.Result("TASK", UUID.randomUUID())), NOW);
        AgentProposal rejected = proposal(); rejected.reject(user, NOW);

        for (AgentProposal p : List.of(accepted, rejected)) {
            assertFalse(p.isPending());
            assertThrows(IllegalStateException.class, () -> p.edit(json.createObjectNode()));
            assertThrows(IllegalStateException.class, () -> p.accept(user, List.of(), NOW));
            assertThrows(IllegalStateException.class, () -> p.reject(user, NOW));
        }
        assertEquals(1, accepted.getResults().size());   // 거절된 뒤의 채택 시도가 결과를 더하지 않았다
        assertTrue(rejected.getResults().isEmpty());
    }

    @Test void originalIsACopySoEditingTheGivenContentDoesNotChangeIt() {
        ObjectNode content = json.createObjectNode().put("title", "원본");
        AgentProposal p = AgentProposal.propose(run(), "TASKS", "제목", "근거", content, NOW);
        content.put("title", "밖에서 고침");   // 넘겨준 객체를 나중에 고쳐도 원본은 그대로여야 한다

        assertEquals("원본", p.getOriginal().get("title").asText());
        assertTrue(p.isEdited());
    }

    @Test void titleLongerThanTheColumnIsCut() {
        AgentProposal p = AgentProposal.propose(run(), "TASKS", "가".repeat(300), "근거", json.createObjectNode(), NOW);
        assertEquals(255, p.getTitle().length());
    }

    @Test void resultsCannotBeChangedFromOutside() {
        AgentProposal p = proposal();
        assertThrows(UnsupportedOperationException.class, () -> p.getResults().add(new AgentProposal.Result("TASK", UUID.randomUUID())));
    }

    @Test void runEndsOnceAndLongErrorIsCut() {
        AgentRun done = run(); done.finish(NOW);
        assertEquals(AgentRun.Status.DONE, done.getStatus());
        assertThrows(IllegalStateException.class, () -> done.fail("다시", NOW));

        AgentRun failed = run(); failed.fail("가".repeat(600), NOW);
        assertEquals(AgentRun.Status.FAILED, failed.getStatus());
        assertEquals(500, failed.getError().length());
        assertEquals(NOW, failed.getFinishedAt());
        assertThrows(IllegalStateException.class, () -> failed.finish(NOW));
    }

    private AgentRun run() { return AgentRun.start(new Project(), user, "ASK", NOW); }

    private AgentProposal proposal() { return AgentProposal.propose(run(), "TASKS", "제목", "근거", json.createObjectNode(), NOW); }
}
