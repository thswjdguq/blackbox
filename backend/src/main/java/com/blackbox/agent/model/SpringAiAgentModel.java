package com.blackbox.agent.model;

import com.blackbox.agent.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring AI를 쓰는 유일한 곳. 공급자(Gemini, Bedrock)는 설정(spring.ai.model.chat)으로 고르고 이 코드는 구분하지 않는다.
 * 공급자가 none이면 ChatModel 빈이 없고, 그때는 available()이 false다.
 */
@Component
public class SpringAiAgentModel implements AgentModel {
    private static final Logger log = LoggerFactory.getLogger(SpringAiAgentModel.class);

    private final ChatModel chatModel;   // 설정이 없으면 null

    public SpringAiAgentModel(Optional<ChatModel> chatModel) {
        this.chatModel = chatModel.orElse(null);
        log.info("에이전트 모델: {}", this.chatModel == null ? "설정되지 않음(spring.ai.model.chat=none)" : this.chatModel.getClass().getSimpleName());
    }

    @Override public boolean available() { return chatModel != null; }

    @Override public String generate(String instructions, String input, List<AgentTool> tools) {
        if (chatModel == null) {
            throw new AgentModelException(AgentModelException.Reason.UNAVAILABLE, "AI 기능이 설정되지 않았습니다", null);
        }
        try {
            long started = System.nanoTime();
            ChatResponse response = ChatClient.create(chatModel).prompt()
                    .system(instructions)
                    .user(input)
                    .tools(tools.stream().map(SpringAiAgentModel::callback).toList())
                    .call()
                    .chatResponse();
            logCall(response, started);
            String text = text(response);
            if (text == null || text.isBlank()) {
                String message = truncated(finishReason(response))
                        ? "모델의 답이 출력 상한에 걸려 잘렸습니다" : "모델이 빈 응답을 돌려줬습니다";
                throw new AgentModelException(AgentModelException.Reason.FAILED, message, null);
            }
            return text;
        } catch (AgentModelException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AgentModelException(AgentModelException.Reason.FAILED, "모델 호출에 실패했습니다", e);
        }
    }

    /** Gemini가 생각(thinking) 조각에 붙이는 표시. 공급자 클래스에 묶이지 않게 이름만 쓴다(GoogleGenAiChatModel.THOUGHT_METADATA_KEY) */
    static final String THOUGHT = "isThought";

    /**
     * 답의 글. 공급자가 답 하나를 결과(Generation) 여러 개로 나눠 줄 수 있다. Gemini는 글 조각(part)마다 결과를 하나씩 만들어,
     * 첫 결과만 읽으면 긴 답이 중간에서 끊긴다(종료 이유는 STOP). 생각 조각은 빼고 순서대로 이어 붙인다.
     * 후보 답을 여러 개 청하지 않으므로 이어 붙여도 다른 답이 섞이지 않는다
     */
    static String text(ChatResponse response) {
        if (response == null) return null;
        return response.getResults().stream().map(Generation::getOutput)
                .filter(m -> !Boolean.TRUE.equals(m.getMetadata().get(THOUGHT)))
                .map(m -> m.getText()).filter(Objects::nonNull).collect(Collectors.joining());
    }

    /** 사용량 한도를 따져 볼 수 있게 호출마다 모델, 걸린 시간, 토큰 수, 마지막 답이 끝난 이유를 남긴다. 도구를 부르며 오간 요청은 합쳐서 한 줄이다 */
    private static void logCall(ChatResponse response, long startedNanos) {
        if (response == null) return;
        Usage usage = response.getMetadata().getUsage();
        log.info("모델 호출 model={} {}ms 입력 {} 출력 {} 토큰 종료={}", response.getMetadata().getModel(),
                (System.nanoTime() - startedNanos) / 1_000_000, usage.getPromptTokens(), usage.getCompletionTokens(),
                finishReason(response));
    }

    private static String finishReason(ChatResponse response) {
        return response == null || response.getResult() == null ? null : response.getResult().getMetadata().getFinishReason();
    }

    /** 공급자마다 이름이 다르다: OpenAI 호환은 length, Bedrock·Anthropic은 max_tokens, Gemini는 MAX_TOKENS */
    private static boolean truncated(String finishReason) {
        return finishReason != null && (finishReason.equalsIgnoreCase("length") || finishReason.equalsIgnoreCase("max_tokens"));
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
