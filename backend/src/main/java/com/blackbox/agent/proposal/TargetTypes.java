package com.blackbox.agent.proposal;

/** 채택 결과의 종류(agent_proposal_results.target_type). 대개 채택이 만든 기록의 종류다. 카드 종류가 늘면 여기에 더한다. */
public final class TargetTypes {
    public static final String DELIVERABLE = "DELIVERABLE";
    public static final String REQUIREMENT = "REQUIREMENT";
    public static final String TASK = "TASK";
    /** 만들어진 기록이 아니라, 수정 카드를 채택해 그 요구사항(target_id)의 충족 확인이 풀렸다는 표시(K-31 5.2) */
    public static final String ASSESSMENT_CLEARED = "REQUIREMENT_ASSESSMENT_CLEARED";

    private TargetTypes() {}
}
