package com.blackbox.agent.model;

import com.blackbox.agent.tool.AgentTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 경계가 우리 타입과 Spring AI 사이를 제대로 잇는지 본다. 실제 모델은 부르지 않는다.
 * 모델이 도구를 부르고 그 결과로 다시 답하는 반복은 Spring AI(ChatClient의 도구 호출 advisor)가 돌리므로, 실제 모델로 따로 확인한다.
 */
class SpringAiAgentModelTest {
    @Test void withoutAProviderItIsUnavailableAndRefusesToRun() {
        SpringAiAgentModel model = new SpringAiAgentModel(Optional.empty());

        assertFalse(model.available());
        AgentModelException e = assertThrows(AgentModelException.class, () -> model.generate("지시", "자료", List.of()));
        assertEquals(AgentModelException.Reason.UNAVAILABLE, e.reason());
    }

    @Test void passesInstructionsInputAndToolsAndReturnsTheText() {
        List<Prompt> seen = new ArrayList<>();
        SpringAiAgentModel model = new SpringAiAgentModel(Optional.of(stub(seen, "업무 3개 중 1개가 끝났습니다")));
        List<String> toolCalls = new ArrayList<>();

        String text = model.generate("기록에 없는 것은 지어내지 않는다", "지금 상태를 알려 줘", List.of(tool("list_tasks", toolCalls)));

        assertTrue(model.available());
        assertEquals("업무 3개 중 1개가 끝났습니다", text);
        Prompt prompt = seen.get(0);
        assertEquals("기록에 없는 것은 지어내지 않는다", text(prompt, MessageType.SYSTEM));
        assertEquals("지금 상태를 알려 줘", text(prompt, MessageType.USER));

        // 우리 도구가 모델이 부를 수 있는 모양으로 넘어갔고, 부르면 우리 도구가 실행된다
        List<ToolCallback> callbacks = ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks();
        assertEquals(1, callbacks.size());
        assertEquals("list_tasks", callbacks.get(0).getToolDefinition().name());
        assertEquals("업무 목록", callbacks.get(0).getToolDefinition().description());
        assertEquals("{\"type\":\"object\",\"properties\":{}}", callbacks.get(0).getToolDefinition().inputSchema());
        assertEquals("[]", callbacks.get(0).call("{\"status\":\"TODO\"}"));
        assertEquals(List.of("{\"status\":\"TODO\"}"), toolCalls);
    }

    @Test void bracesInInstructionsAndInputAreSentAsWritten() {
        // 지시문에는 JSON 형식 예시가, 안내문에는 중괄호가 들어갈 수 있다. 치환 문법으로 읽히면 안 된다
        List<Prompt> seen = new ArrayList<>();
        SpringAiAgentModel model = new SpringAiAgentModel(Optional.of(stub(seen, "답")));
        String instructions = "아래 형식으로 답한다: {\"tasks\":[{\"title\":\"...\"}]}";
        String input = "제출물 {중간 보고서}는 <b>10쪽</b>, 형식은 {name} 그리고 $변수$";

        model.generate(instructions, input, List.of());

        assertEquals(instructions, text(seen.get(0), MessageType.SYSTEM));
        assertEquals(input, text(seen.get(0), MessageType.USER));
    }

    @Test void providerFailureAndEmptyAnswerBecomeOurException() {
        ChatModel failing = prompt -> { throw new IllegalStateException("429 quota exceeded"); };
        AgentModelException failed = assertThrows(AgentModelException.class,
                () -> new SpringAiAgentModel(Optional.of(failing)).generate("지시", "자료", List.of()));
        assertEquals(AgentModelException.Reason.FAILED, failed.reason());
        assertEquals("모델 호출에 실패했습니다", failed.getMessage());   // 공급자의 문구를 사용자에게 내보내지 않는다
        assertEquals("429 quota exceeded", failed.getCause().getMessage());

        AgentModelException empty = assertThrows(AgentModelException.class,
                () -> new SpringAiAgentModel(Optional.of(stub(new ArrayList<>(), " "))).generate("지시", "자료", List.of()));
        assertEquals(AgentModelException.Reason.FAILED, empty.reason());
        assertEquals("모델이 빈 응답을 돌려줬습니다", empty.getMessage());
    }

    @Test void anEmptyAnswerCutByTheOutputLimitSaysSo() {
        // 실서버에서 상태 요약이 출력 상한에 걸려 빈 답으로 끝났다. 원인을 로그에서 바로 알 수 있게 다른 문구로 올린다
        for (String reason : List.of("length", "MAX_TOKENS")) {
            ChatModel cut = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(""),
                    ChatGenerationMetadata.builder().finishReason(reason).build())));
            AgentModelException e = assertThrows(AgentModelException.class,
                    () -> new SpringAiAgentModel(Optional.of(cut)).generate("지시", "자료", List.of()));
            assertEquals(AgentModelException.Reason.FAILED, e.reason());
            assertEquals("모델의 답이 출력 상한에 걸려 잘렸습니다", e.getMessage(), reason);
        }
    }

    /**
     * Gemini는 글 조각마다 결과(Generation)를 하나씩 만든다. 첫 결과만 읽으면 긴 응답이 STOP으로 끝났는데도 중간에서 끊겼다(A-80).
     * 결과를 순서대로 이어 붙이고, 추론 조각과 도구 호출 자리(빈 글)는 넣지 않는다
     */
    @Test void anAnswerSplitIntoSeveralResultsIsJoinedWithoutThoughts() {
        ChatModel split = prompt -> new ChatResponse(List.of(
                new Generation(AssistantMessage.builder().content("먼저 기록을 살펴보자").properties(java.util.Map.of("isThought", true)).build()),
                new Generation(new AssistantMessage("{\"text\": \"목차입니다\", ")),
                new Generation(new AssistantMessage("")),
                new Generation(new AssistantMessage("\"proposals\": []}"))));
        assertEquals("{\"text\": \"목차입니다\", \"proposals\": []}", new SpringAiAgentModel(Optional.of(split)).generate("지시", "자료", List.of()));
    }

    /** 실제 공급자처럼 도구를 받을 수 있는 옵션을 내놓는다. Spring AI 2.0은 모델의 옵션이 이 모양일 때 도구를 실어 보낸다 */
    private static ChatModel stub(List<Prompt> seen, String answer) {
        return new ChatModel() {
            @Override public ChatResponse call(Prompt prompt) {
                seen.add(prompt);
                return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
            }
            @Override public ChatOptions getOptions() { return ToolCallingChatOptions.builder().build(); }
        };
    }

    private static String text(Prompt prompt, MessageType type) {
        return prompt.getInstructions().stream().filter(m -> m.getMessageType() == type).map(Message::getText).findFirst().orElseThrow();
    }

    private static AgentTool tool(String name, List<String> calls) {
        return new AgentTool() {
            @Override public String name() { return name; }
            @Override public String description() { return "업무 목록"; }
            @Override public String inputSchema() { return "{\"type\":\"object\",\"properties\":{}}"; }
            @Override public String call(String argumentsJson) { calls.add(argumentsJson); return "[]"; }
        };
    }
}
