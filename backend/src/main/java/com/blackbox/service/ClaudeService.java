package com.blackbox.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class ClaudeService extends AbstractLlmClient {

    private final WebClient webClient;
    private final String model;
    private final String apiKey;

    public ClaudeService(
            @Value("${external.claude.base-url}") String baseUrl,
            @Value("${external.claude.model}") String model,
            @Value("${external.claude.api-key}") String apiKey) {
        this.model  = model;
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ── provider 구현 (Template method) ─────────────────────────────────

    @Override
    protected String callModel(String userPrompt, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("CLAUDE_API_KEY가 설정되지 않았습니다");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        Map<?, ?> response = webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) throw new RuntimeException("Claude API 응답 없음");

        List<?> content = (List<?>) response.get("content");
        if (content == null || content.isEmpty()) throw new RuntimeException("Claude API 응답 비어 있음");

        Map<?, ?> first = (Map<?, ?>) content.get(0);
        return (String) first.get("text");
    }

    @Override
    protected String callModelWithSystem(String systemPrompt, String userPrompt, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("CLAUDE_API_KEY가 설정되지 않았습니다");
        }

        Map<String, Object> body = Map.of(
                "model",      model,
                "max_tokens", maxTokens,
                "system",     systemPrompt,
                "messages",   List.of(Map.of("role", "user", "content", userPrompt))
        );

        Map<?, ?> response = webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) throw new RuntimeException("Claude API 응답 없음");
        List<?> content = (List<?>) response.get("content");
        if (content == null || content.isEmpty()) throw new RuntimeException("Claude API 응답 비어 있음");
        return (String) ((Map<?, ?>) content.get(0)).get("text");
    }
}
