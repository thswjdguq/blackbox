package com.blackbox.agent.run;

import com.blackbox.agent.AgentProposal;

import java.util.List;
import java.util.UUID;

/** 실행이 진행되며 알리는 일(K-30 4장). 실행은 done이나 error 중 하나로 끝난다. */
public interface AgentEvents {
    void step(String tool, String label);
    /** 실행이 쓴 과제 안내문(K-31 4장). 모델을 부르기 전, 첫 step으로 나간다. created는 이번에 새로 저장했는가다. 안내문을 쓰지 않는 실행은 부르지 않는다 */
    default void brief(UUID briefId, String title, boolean created) {}
    void text(String text);
    void proposal(AgentProposal proposal);
    void done(UUID runId, List<UUID> proposalIds);
    void error(String detail);
}
