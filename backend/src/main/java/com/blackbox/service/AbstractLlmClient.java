package com.blackbox.service;

import com.blackbox.dto.ActionItemDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * LLM 공급자 공통 로직 추상 클래스 (Task 26).
 * provider 차이(URI·헤더·바디·응답파싱)만 각 구현체에서 override.
 *
 * Template method:
 *   - callModel(userPrompt, maxTokens)
 *   - callModelWithSystem(systemPrompt, userPrompt, maxTokens)
 */
public abstract class AbstractLlmClient implements LlmClient {

    // ── Template method — 각 provider가 구현 ─────────────────────────────

    protected abstract String callModel(String userPrompt, int maxTokens);

    protected abstract String callModelWithSystem(String systemPrompt, String userPrompt, int maxTokens);

    // ── 공개 메서드 공통 구현 ─────────────────────────────────────────────

    @Override
    public String summarizeMeeting(String title, String purpose, String notes, String decisions) {
        return callModel(buildSummaryPrompt(title, purpose, notes, decisions), 1500);
    }

    @Override
    public List<ActionItemDto> extractStructuredActionItems(String notes, String decisions) {
        String systemPrompt =
            "너는 회의록에서 액션아이템을 추출하는 assistant야. " +
            "반드시 JSON 배열만 반환하고 다른 텍스트는 절대 포함하지 마.";
        String userPrompt =
            "아래 회의록에서 액션아이템을 추출해줘. " +
            "각 항목은 {\"title\": \"할 일 제목\", \"assignee\": \"담당자 이름 또는 null\", " +
            "\"due_date\": \"YYYY-MM-DD 또는 null\", \"priority\": \"HIGH/MEDIUM/LOW\"} 형식으로.\n\n" +
            "회의록:\n" + nvl(notes) + "\n\n결정사항:\n" + nvl(decisions);
        String raw = callModelWithSystem(systemPrompt, userPrompt, 1200);
        return parseStructured(raw, systemPrompt, userPrompt);
    }

    @Override
    public List<String> extractActionItems(String notes, String decisions) {
        return parseLines(callModel(buildExtractPrompt(notes, decisions), 800));
    }

    @Override
    public String rawCall(String userPrompt, int maxTokens) {
        return callModel(userPrompt, maxTokens);
    }

    // ── 공통 프롬프트 빌더 ────────────────────────────────────────────────

    protected String buildSummaryPrompt(String title, String purpose, String notes, String decisions) {
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

    // NOTE: buildExtractPrompt는 provider별로 원래 다르다 — ClaudeService 버전에는
    // "출력 예시" 섹션이 있고 OpenAiService 버전에는 없다(기존 코드의 의도적 차이).
    // 이 기본 구현은 ClaudeService 버전(출력 예시 포함)이며, OpenAiService가 override한다.
    // 동작 보존 원칙(PRINCIPLES §4)에 따라 차이를 그대로 유지한다. (Task 26 검토에서 보존 확정)
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

                출력 예시:
                API 명세서 작성
                로그인 버그 수정
                다음 회의 일정 조율
                """.formatted(nvl(notes), nvl(decisions));
    }

    // ── 공통 응답 파싱 ────────────────────────────────────────────────────

    protected List<ActionItemDto> parseStructured(String raw, String systemPrompt, String userPrompt) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            String json = extractJsonArray(raw);
            return mapper.readValue(json, new TypeReference<List<ActionItemDto>>() {});
        } catch (Exception first) {
            // 1회 재시도
            try {
                String retried = callModelWithSystem(systemPrompt, userPrompt, 1200);
                String json = extractJsonArray(retried);
                return mapper.readValue(json, new TypeReference<List<ActionItemDto>>() {});
            } catch (Exception second) {
                throw new RuntimeException("AI 응답을 JSON으로 파싱할 수 없습니다: " + second.getMessage());
            }
        }
    }

    protected String extractJsonArray(String raw) {
        if (raw == null) throw new RuntimeException("AI 응답이 null입니다");
        int start = raw.indexOf('[');
        int end   = raw.lastIndexOf(']');
        if (start < 0 || end < 0) throw new RuntimeException("JSON 배열을 찾을 수 없습니다");
        return raw.substring(start, end + 1);
    }

    protected List<String> parseLines(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return raw.lines()
                .map(String::strip)
                .filter(l -> !l.isBlank())
                .limit(8)
                .toList();
    }

    protected String nvl(String s) {
        return s != null ? s : "";
    }
}
