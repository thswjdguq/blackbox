package com.blackbox.agent.tool;

/**
 * 모델이 부를 수 있는 읽기 도구 하나(K-30 5장). 우리 타입만으로 정의해, 도구는 어느 모델 라이브러리를 쓰는지 모른다.
 * 도구를 더할 때는 이 인터페이스의 구현을 하나 등록한다.
 */
public interface AgentTool {
    /** 모델이 부르는 이름. 예: list_tasks */
    String name();

    /** 모델이 언제 이 도구를 쓸지 판단하는 설명 */
    String description();

    /** 인자의 JSON Schema. 인자가 없으면 빈 객체 스키마 */
    String inputSchema();

    /** 인자(JSON)를 받아 결과(JSON)를 돌려준다. 쓰기는 하지 않는다 */
    String call(String argumentsJson);
}
