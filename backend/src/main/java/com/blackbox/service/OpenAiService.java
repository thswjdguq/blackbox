package com.blackbox.service;

import org.springframework.beans.factory.annotation.Value;
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
@Service
public class OpenAiService {

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

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ── 회의록 AI 요약 ────────────────────────────────────────────────────

    public String summarizeMeeting(String title, String purpose, String notes, String decisions) {
        return call(buildSummaryPrompt(title, purpose, notes, decisions), 1500);
    }

    // ── AI 액션아이템 추출 ────────────────────────────────────────────────

    public List<String> extractActionItems(String notes, String decisions) {
        String raw = call(buildExtractPrompt(notes, decisions), 800);
        return parseLines(raw);
    }

    /** GoogleCalendarService 등 외부 서비스에서 직접 호출하는 공개 메서드 (ClaudeService.rawCall과 동일 시그니처) */
    public String rawCall(String userPrompt, int maxTokens) {
        return call(userPrompt, maxTokens);
    }

    // ── internal ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String call(String userPrompt, int maxTokens) {
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

    private String buildSummaryPrompt(String title, String purpose, String notes, String decisions) {
        return """
                당신은 회의록 요약 전문가입니다. 아래 회의록을 한국어로 간결하게 요약해 주세요.

                회의 제목: %s
                안건: %s
                회의 내용:
                %s

                결정사항:
                %s

                다음 형식으로 요약해 주세요:
                ## 핵심 요약
                (2~3문장으로 회의 전체를 요약)

                ## 주요 결정사항
                - (결정사항을 bullet point로)

                ## 다음 단계
                - (다음에 해야 할 일들을 bullet point로)

                마크다운 형식을 유지하고, 불필요한 서론 없이 바로 시작하세요.
                """.formatted(nvl(title), nvl(purpose), nvl(notes), nvl(decisions));
    }

    private String buildExtractPrompt(String notes, String decisions) {
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

    private List<String> parseLines(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return raw.lines()
                .map(String::strip)
                .filter(l -> !l.isBlank())
                .limit(8)
                .toList();
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
