package com.blackbox.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class ClaudeService {

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

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ── 회의록 AI 요약 ──────────────────────────────────────────────────────

    public String summarizeMeeting(String title, String purpose, String notes, String decisions) {
        String prompt = buildSummaryPrompt(title, purpose, notes, decisions);
        return callClaude(prompt, 1500);
    }

    // ── AI 액션아이템 추출 ────────────────────────────────────────────────────

    public List<String> extractActionItems(String notes, String decisions) {
        String prompt = buildExtractPrompt(notes, decisions);
        String raw = callClaude(prompt, 800);
        return parseLines(raw);
    }

    /** 외부 서비스(GoogleCalendarService 등)에서 직접 호출하는 공개 메서드 */
    public String rawCall(String userPrompt, int maxTokens) {
        return callClaude(userPrompt, maxTokens);
    }

    // ── internal ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String callClaude(String userPrompt, int maxTokens) {
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
                """.formatted(
                nvl(title), nvl(purpose), nvl(notes), nvl(decisions));
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

                출력 예시:
                API 명세서 작성
                로그인 버그 수정
                다음 회의 일정 조율
                """.formatted(nvl(notes), nvl(decisions));
    }

    /** 응답 텍스트를 줄 단위로 파싱, 빈 줄/공백 제거 */
    private List<String> parseLines(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return raw.lines()
                .map(String::strip)
                .filter(l -> !l.isBlank())
                .limit(8)
                .toList();
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
