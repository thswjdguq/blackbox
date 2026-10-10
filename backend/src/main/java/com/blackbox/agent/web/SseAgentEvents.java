package com.blackbox.agent.web;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.proposal.ProposalResponse;
import com.blackbox.agent.run.AgentEvents;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 실행이 알리는 일을 이벤트 스트림으로 내보낸다(K-30 4장).
 * 화면이 연결을 끊었거나 스트림이 이미 끝났으면 조용히 버린다. 실행 쪽으로 예외를 올리지 않는다.
 */
class SseAgentEvents implements AgentEvents {
    private final SseEmitter emitter;
    private boolean closed;

    SseAgentEvents(SseEmitter emitter) { this.emitter = emitter; }

    @Override public void step(String tool, String label) { send("step", new Step(label, tool), false); }
    @Override public void text(String text) { send("text", new Text(text), false); }
    @Override public void proposal(AgentProposal proposal) { send("proposal", ProposalResponse.from(proposal), false); }
    @Override public void done(UUID runId, List<UUID> proposalIds) { send("done", new Done(runId, proposalIds), true); }
    @Override public void error(String detail) { send("error", new Failure(detail), true); }

    private synchronized void send(String name, Object data, boolean last) {
        if (closed) return;
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            if (last) { closed = true; emitter.complete(); }
        } catch (IOException | IllegalStateException e) {
            closed = true;
        }
    }

    record Step(String label, String tool) {}
    record Text(String text) {}
    record Done(UUID runId, List<UUID> proposalIds) {}
    record Failure(String detail) {}
}
