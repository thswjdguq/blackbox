package com.blackbox.agent;

import java.util.UUID;

/** 다른 담당이 "이 기록이 AI 제안에서 나왔는가"를 물을 때 쓴다(K-30 7장). 기여도에서 어떻게 다룰지는 묻는 쪽이 정한다. */
public interface AgentOriginReader {
    /** targetType은 "DELIVERABLE"·"REQUIREMENT"·"TASK" */
    boolean createdFromProposal(UUID projectId, String targetType, UUID targetId);
}
