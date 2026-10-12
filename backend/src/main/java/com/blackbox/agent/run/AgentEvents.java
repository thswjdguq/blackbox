package com.blackbox.agent.run;

import com.blackbox.agent.AgentProposal;

import java.util.List;
import java.util.UUID;

/** 실행이 진행되며 알리는 일(K-30 4장). 실행은 done이나 error 중 하나로 끝난다. */
public interface AgentEvents {
    void step(String tool, String label);
    void text(String text);
    void proposal(AgentProposal proposal);
    void done(UUID runId, List<UUID> proposalIds);
    void error(String detail);
}
