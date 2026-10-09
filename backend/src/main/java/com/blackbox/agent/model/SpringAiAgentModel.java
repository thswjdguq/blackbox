package com.blackbox.agent.model;

import com.blackbox.agent.tool.AgentTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Spring AI를 쓰는 유일한 곳. 공급자(Gemini, Bedrock)는 설정(spring.ai.model.chat)으로 고르고 이 코드는 구분하지 않는다.
 * 공급자가 none이면 ChatModel 빈이 없고, 그때는 available()이 false다.
 */
@Component
public class SpringAiAgentModel implements AgentModel {
    private final ChatModel chatModel;   // 설정이 없으면 null

    public SpringAiAgentModel(Optional<ChatModel> chatModel) {
        this.chatModel = chatModel.orElse(null);
    }

    @Override public boolean available() { return chatModel != null; }

    @Override public String generate(String instructions, String input, List<AgentTool> tools) {
        if (chatModel == null) {
            throw new AgentModelException(AgentModelException.Reason.UNAVAILABLE, "AI 기능이 설정되지 않았습니다", null);
        }
        try {
            String text = ChatClient.create(chatModel).prompt()
                    .system(instructions)
                    .user(input)
                    .toolCallbacks(tools.stream().map(SpringAiAgentModel::callback).toList())
                    .call()
                    .content();
            if (text == null || text.isBlank()) {
                throw new AgentModelException(AgentModelException.Reason.FAILED, "모델이 빈 응답을 돌려줬습니다", null);
            }
            return text;
        } catch (AgentModelException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AgentModelException(AgentModelException.Reason.FAILED, "모델 호출에 실패했습니다", e);
        }
    }

    /** 우리 도구를 Spring AI가 부를 수 있는 모양으로 감싼다. 도구 쪽은 Spring AI를 모른다 */
    private static ToolCallback callback(AgentTool tool) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(tool.name()).description(tool.description()).inputSchema(tool.inputSchema()).build();
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return definition; }
            @Override public String call(String toolInput) { return tool.call(toolInput); }
        };
    }
}
