package com.blackbox.agent.run;

import com.blackbox.agent.AgentProposal;

import java.util.List;
import java.util.UUID;

/** 실행이 진행되며 알리는 일(K-30 4장). 실행은 done이나 error 중 하나로 끝난다. */
public interface AgentEvents {
    void step(String tool, String label);
    /** 실행이 쓴 과제 안내문(K-31 4·5장). 모델을 부르기 전, 첫 step으로 나간다. 안내문을 쓰지 않는 실행은 부르지 않는다 */
    default void brief(UUID briefId, String title, BriefUse use) {}

    /** 실행이 안내문을 어떻게 썼는가. 알림 문구가 이것으로 정해진다 */
    enum BriefUse {
        /** 붙여 넣은 글을 이번에 새로 저장했다(계획 만들기) */
        CREATED,
        /** 본문이 같은 저장된 안내문을 다시 썼다(계획 만들기) */
        REUSED,
        /** 제출물에 걸린 안내문을 읽었다(요구사항 찾기) */
        READ
    }
    void text(String text);
    void proposal(AgentProposal proposal);
    void done(UUID runId, List<UUID> proposalIds);
    void error(String detail);
}
