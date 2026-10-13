package com.blackbox.agent.model;

import com.blackbox.agent.AgentProperties;
import com.blackbox.agent.tool.AgentTool;
import com.blackbox.service.OpenAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;

/** 별도 테스트 환경 어댑터. 읽기 도구만 모델에 공개하고 제안 채택 명령은 공개하지 않는다. */
@Component
public class OpenAiAgentModel implements AgentModel {
    private final OpenAiService client;
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    public OpenAiAgentModel(OpenAiService client, AgentProperties properties, ObjectMapper mapper) {
        this.client=client; this.properties=properties; this.mapper=mapper;
    }
    @Override public boolean available() { return client.isConfigured(); }

    @Override public String generate(String instructions, String input, List<AgentTool> tools) {
        if (!available()) throw new AgentModelException(AgentModelException.Reason.UNAVAILABLE,"OpenAI 연결이 설정되지 않았습니다.",null);
        try {
            var history=new ArrayList<Map<String,Object>>();
            history.add(Map.of("role","system","content",instructions));
            history.add(Map.of("role","user","content",input));
            var definitions=new ArrayList<Map<String,Object>>();
            Map<String,AgentTool> allowed=new HashMap<>();
            for (AgentTool tool:tools) {
                allowed.put(tool.name(),tool);
                definitions.add(Map.of("type","function","function",Map.of("name",tool.name(),"description",tool.description(),"parameters",mapper.readTree(tool.inputSchema()))));
            }
            long deadline=System.nanoTime()+properties.getRunTimeLimit().toNanos();
            int calls=0;
            while (true) {
                long remaining=deadline-System.nanoTime();
                if (remaining<=0) throw new IllegalStateException("실행 시간 상한 초과");
                Duration timeout=Duration.ofNanos(Math.min(remaining,properties.getModelRequestTimeout().toNanos()));
                Map<?,?> response=client.agentChat(history,definitions,timeout);
                if (response==null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) throw new IllegalStateException("빈 모델 응답");
                Map<?,?> choice=(Map<?,?>)choices.get(0);
                Map<?,?> message=(Map<?,?>)choice.get("message");
                if (!(message.get("tool_calls") instanceof List<?> requests) || requests.isEmpty()) {
                    if (!(message.get("content") instanceof String text) || text.isBlank()) throw new IllegalStateException("빈 모델 답변");
                    return text;
                }
                history.add(Map.of("role","assistant","tool_calls",requests));
                for (Object item:requests) {
                    if (++calls>properties.getMaxToolCalls()) throw new IllegalStateException("조회 횟수 상한 초과");
                    Map<?,?> request=(Map<?,?>)item, function=(Map<?,?>)request.get("function");
                    AgentTool tool=allowed.get(function.get("name"));
                    if (tool==null) throw new IllegalStateException("허용되지 않은 도구");
                    String output=tool.call((String)function.get("arguments"));
                    history.add(Map.of("role","tool","tool_call_id",request.get("id"),"content",output));
                }
            }
        } catch (Exception e) {
            // 공급자 응답 본문에는 요청 정보가 포함될 수 있어 화면/실행 기록에는 원문을 전달하지 않는다.
            throw new AgentModelException(AgentModelException.Reason.FAILED,"OpenAI 요청을 처리하지 못했습니다. 연결과 사용 한도를 확인하세요.",e);
        }
    }
}
