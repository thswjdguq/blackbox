package com.blackbox.agent.model;

import com.blackbox.agent.tool.AgentTool;
import java.util.List;

/**
 * 모델 호출의 경계. 에이전트의 나머지 코드는 이 인터페이스만 알고, 어느 공급자와 라이브러리를 쓰는지 모른다.
 * 공급자나 라이브러리가 바뀌면 이 인터페이스의 구현만 고친다.
 */
public interface AgentModel {
    /** 모델이 설정돼 있어 부를 수 있는가. 아니면 화면이 에이전트 버튼을 숨긴다(K-30 3장) */
    boolean available();

    /**
     * 지시문과 자료를 주고 마지막 글을 받는다. 모델이 도구를 부르면 그 결과를 다시 넘겨 가며 답이 나올 때까지 돈다.
     *
     * @param instructions 무엇을 어떤 형식으로 답할지. 리소스 파일에서 온다
     * @param input        사용자가 준 자료(안내문, 질문). 지시가 아니라 자료로 넘긴다
     * @throws AgentModelException 모델을 쓸 수 없거나 호출이 실패했을 때
     */
    String generate(String instructions, String input, List<AgentTool> tools);
}
