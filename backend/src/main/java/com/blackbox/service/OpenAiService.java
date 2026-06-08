package com.blackbox.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI API 연동 서비스.
 * Claude API 키가 없을 때 폴백으로 사용.
 * 필요 환경변수: OPENAI_API_KEY
 */
@Order(2)
@Service
public class OpenAiService extends AbstractLlmClient {

    private final WebClient webClient;
    private final String model;
    private final String apiKey;

    public OpenAiService(
            @Value("${external.openai.base-url}") String baseUrl,
            @Value("${external.openai.model}")    String model,
            @Value("${external.openai.api-key}")  String apiKey) {
        this.model  = model;
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ── provider 구현 (Template method) ─────────────────────────────────

    @Override
    protected String callModel(String userPrompt, int maxTokens) {
        if (!isConfigured()) {
            throw new IllegalStateException("OPENAI_API_KEY가 설정되지 않았습니다");
        }

        Map<String, Object> body = Map.of(
                "model",      model,
                "max_tokens", maxTokens,
                "messages",   List.of(Map.of("role", "user", "content", userPrompt))
        );

        Map<?, ?> response = webClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) throw new RuntimeException("OpenAI API 응답 없음");

        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("OpenAI API 응답 비어 있음");

        Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
        return (String) message.get("content");
    }

    @Override
    protected String callModelWithSystem(String systemPrompt, String userPrompt, int maxTokens) {
        if (!isConfigured()) {
            throw new IllegalStateException("OPENAI_API_KEY가 설정되지 않았습니다");
        }

        Map<String, Object> body = Map.of(
                "model",      model,
                "max_tokens", maxTokens,
                "messages",   List.of(
                        Map.of("role", "system",  "content", systemPrompt),
                        Map.of("role", "user",    "content", userPrompt)
                )
        );

        Map<?, ?> response = webClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) throw new RuntimeException("OpenAI API 응답 없음");
        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("OpenAI API 응답 비어 있음");
        Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
        return (String) message.get("content");
    }

    // NOTE: OpenAI 버전 buildExtractPrompt는 ClaudeService 버전과 의도적으로 다르다
    // ("출력 예시" 섹션 없음). 동작 보존을 위해 override로 기존 OpenAI 프롬프트를 정확히 보존.
    @Override
    protected String buildExtractPrompt(String notes, String decisions) {
        return """
                아래 회의록에서 구체적인 실행 가능한 액션아이템(Action Item)을 추출해 주세요.

                회의 내용:
                %s

                결정사항:
                %s

                규칙:
                - 각 액션아이템은 한 줄에 하나씩
                - "~하기", "~작성", "~검토" 등 동사로 끝나는 짧은 태스크 제목 형태
                - 추출된 아이템만 출력, 번호나 기호 없이 텍스트만
                - 최대 8개
                - 액션아이템이 없으면 빈 줄만 출력
                """.formatted(nvl(notes), nvl(decisions));
    }
}
