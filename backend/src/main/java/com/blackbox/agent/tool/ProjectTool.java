package com.blackbox.agent.tool;

import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * 프로젝트 기록을 읽는 도구 하나(K-30 5장). 빈으로 등록하면 AgentToolbox가 찾아 쓴다.
 * 요청한 사람으로 기존 조회를 부르고, 모델로 나가도 되는 것만 골라 돌려준다. 이메일과 파일 내용은 넣지 않는다.
 */
public interface ProjectTool {
    String NO_ARGUMENTS = "{\"type\":\"object\",\"properties\":{}}";

    String name();

    /** 실행 중에 화면에 보일 말 */
    String label();

    /** 모델이 읽는 설명 */
    String description();

    /** 인자의 JSON Schema */
    default String inputSchema() { return NO_ARGUMENTS; }

    /**
     * 돌려준 값은 JSON으로 바뀌어 모델에 전달된다. 목록(List)을 돌려주면 도구함이 건수 상한에서 자른다.
     * 인자가 빠졌거나 형식이 틀리면 BadArguments를 던진다.
     */
    Object read(UUID projectId, User user, JsonNode arguments);

    /** 모델이 인자를 잘못 채워 불렀다. 이유는 모델에 그대로 전달된다 */
    class BadArguments extends RuntimeException {
        public BadArguments(String message) { super(message); }
    }
}
