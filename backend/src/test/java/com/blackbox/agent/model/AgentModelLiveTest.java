package com.blackbox.agent.model;

import com.blackbox.agent.tool.AgentTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실제 모델로만 볼 수 있는 것을 본다: 모델이 우리 도구를 부르고 그 결과로 답하는 반복.
 * 키(SPRING_AI_GOOGLE_GENAI_API_KEY)가 있을 때만 돌고, 없으면 건너뛴다. 지어낸 숫자만 보낸다.
 */
@SpringBootTest(properties = "spring.ai.model.chat=google-genai")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_AI_GOOGLE_GENAI_API_KEY", matches = ".+")
class AgentModelLiveTest {
    @Autowired AgentModel model;

    @Test void modelCallsOurToolAndAnswersFromItsResult() {
        List<String> calls = new ArrayList<>();
        AgentTool countTasks = new AgentTool() {
            @Override public String name() { return "count_tasks"; }
            @Override public String description() { return "프로젝트의 업무 개수를 돌려준다"; }
            @Override public String inputSchema() { return "{\"type\":\"object\",\"properties\":{}}"; }
            @Override public String call(String argumentsJson) { calls.add(argumentsJson); return "{\"total\":37,\"done\":12}"; }
        };

        String text = model.generate("도구로 조회한 값만 답한다. 추측하지 않는다.",
                "이 프로젝트의 업무는 모두 몇 개이고 그중 몇 개가 끝났어?", List.of(countTasks));

        assertTrue(model.available());
        assertFalse(calls.isEmpty(), "모델이 도구를 부르지 않았다");
        assertTrue(text.contains("37") && text.contains("12"), text);
    }
}
