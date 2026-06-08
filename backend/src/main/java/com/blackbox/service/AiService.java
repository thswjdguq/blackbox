package com.blackbox.service;

import com.blackbox.dto.ActionItemDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Claude → OpenAI 폴백 오케스트레이터.
 * Task 27 — AiService 폴백 중앙화.
 *
 * Spring이 @Order 순서대로 List<LlmClient>를 주입하므로
 * ClaudeService(@Order(1)) → OpenAiService(@Order(2)) 우선순위가 자동으로 보장된다.
 */
@Service
public class AiService {

    private final List<LlmClient> clients;

    public AiService(List<LlmClient> clients) {
        this.clients = clients;
    }

    /** 설정된 첫 번째 LlmClient를 반환. 하나도 없으면 IllegalStateException. */
    private LlmClient active() {
        return clients.stream()
                .filter(LlmClient::isConfigured)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "AI API 키가 설정되지 않았습니다 (CLAUDE_API_KEY 또는 OPENAI_API_KEY 필요)"));
    }

    public String summarizeMeeting(String title, String purpose, String notes, String decisions) {
        return active().summarizeMeeting(title, purpose, notes, decisions);
    }

    public List<ActionItemDto> extractStructuredActionItems(String notes, String decisions) {
        return active().extractStructuredActionItems(notes, decisions);
    }

    public String rawCall(String userPrompt, int maxTokens) {
        return active().rawCall(userPrompt, maxTokens);
    }
}
