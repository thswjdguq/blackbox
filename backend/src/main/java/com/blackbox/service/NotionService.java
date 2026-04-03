package com.blackbox.service;

import com.blackbox.entity.Meeting;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Notion API 연동 서비스.
 * 회의록을 Notion 페이지로 내보냅니다.
 * 필요 환경변수: NOTION_API_KEY, NOTION_PARENT_PAGE_ID
 */
@Service
public class NotionService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WebClient webClient;
    private final String    parentPageId;
    private final String    apiKey;

    public NotionService(
            @Value("${external.notion.base-url}") String baseUrl,
            @Value("${external.notion.api-key}")  String apiKey,
            @Value("${external.notion.parent-page-id}") String parentPageId) {
        this.apiKey       = apiKey;
        this.parentPageId = parentPageId;
        this.webClient    = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Notion-Version", "2022-06-28")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ── 공개 API ─────────────────────────────────────────────────────────

    /**
     * 회의록을 Notion 페이지로 내보냅니다.
     *
     * @param meeting   회의 엔티티
     * @param aiSummary Claude가 생성한 AI 요약 (null 허용)
     * @return 생성된 Notion 페이지 URL
     */
    public String exportMeeting(Meeting meeting, String aiSummary) {
        validateConfig();

        String date  = meeting.getMeetingDate() != null
                ? meeting.getMeetingDate().format(DATE_FMT) : "날짜 미정";
        String title = "회의록 — " + meeting.getTitle() + " (" + date + ")";

        List<Map<String, Object>> children = buildBlocks(meeting, aiSummary);

        Map<String, Object> body = Map.of(
                "parent",     Map.of("type", "page_id", "page_id", parentPageId),
                "properties", Map.of(
                        "title", Map.of("title", List.of(
                                Map.of("text", Map.of("content", title))
                        ))
                ),
                "children", children
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient.post()
                .uri("/v1/pages")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) throw new RuntimeException("Notion API 응답 없음");

        // Notion 페이지 URL 조합
        String pageId = (String) response.get("id");
        if (pageId == null) throw new RuntimeException("Notion 페이지 ID 없음");

        return "https://www.notion.so/" + pageId.replace("-", "");
    }

    // ── Notion 블록 빌더 ─────────────────────────────────────────────────

    private List<Map<String, Object>> buildBlocks(Meeting meeting, String aiSummary) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        // ── 기본 정보 섹션 ────────────────────────────────────────────────
        blocks.add(heading2("📋 회의 기본 정보"));
        blocks.add(callout("🗓  " + (meeting.getMeetingDate() != null
                ? meeting.getMeetingDate().format(DATE_FMT) : "날짜 미정"), "📅"));

        if (meeting.getPurpose() != null && !meeting.getPurpose().isBlank()) {
            blocks.add(paragraph("📌 안건: " + meeting.getPurpose()));
        }

        blocks.add(divider());

        // ── 회의 내용 ────────────────────────────────────────────────────
        blocks.add(heading2("📝 회의 내용"));
        if (meeting.getNotes() != null && !meeting.getNotes().isBlank()) {
            // 긴 텍스트를 2000자 단위로 분할 (Notion 블록 제한)
            for (String chunk : splitText(meeting.getNotes(), 1900)) {
                blocks.add(paragraph(chunk));
            }
        } else {
            blocks.add(paragraph("(회의 내용 없음)"));
        }

        blocks.add(divider());

        // ── 결정사항 ─────────────────────────────────────────────────────
        blocks.add(heading2("✅ 결정사항"));
        if (meeting.getDecisions() != null && !meeting.getDecisions().isBlank()) {
            for (String line : meeting.getDecisions().split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    blocks.add(bulletItem(trimmed));
                }
            }
        } else {
            blocks.add(paragraph("(결정사항 없음)"));
        }

        // ── AI 요약 ──────────────────────────────────────────────────────
        if (aiSummary != null && !aiSummary.isBlank()) {
            blocks.add(divider());
            blocks.add(heading2("🤖 AI 요약 (Claude)"));
            for (String chunk : splitText(aiSummary, 1900)) {
                blocks.add(paragraph(chunk));
            }
        }

        // ── Blackbox 서명 ────────────────────────────────────────────────
        blocks.add(divider());
        blocks.add(paragraph("📎 이 페이지는 Team Blackbox에서 자동 생성되었습니다."));

        return blocks;
    }

    // ── 블록 생성 헬퍼 ───────────────────────────────────────────────────

    private Map<String, Object> heading2(String text) {
        return Map.of(
                "object", "block",
                "type", "heading_2",
                "heading_2", Map.of(
                        "rich_text", List.of(
                                Map.of("type", "text", "text", Map.of("content", text))
                        )
                )
        );
    }

    private Map<String, Object> paragraph(String text) {
        return Map.of(
                "object", "block",
                "type", "paragraph",
                "paragraph", Map.of(
                        "rich_text", List.of(
                                Map.of("type", "text", "text", Map.of("content", text))
                        )
                )
        );
    }

    private Map<String, Object> bulletItem(String text) {
        return Map.of(
                "object", "block",
                "type", "bulleted_list_item",
                "bulleted_list_item", Map.of(
                        "rich_text", List.of(
                                Map.of("type", "text", "text", Map.of("content", text))
                        )
                )
        );
    }

    private Map<String, Object> callout(String text, String emoji) {
        return Map.of(
                "object", "block",
                "type", "callout",
                "callout", Map.of(
                        "rich_text", List.of(
                                Map.of("type", "text", "text", Map.of("content", text))
                        ),
                        "icon", Map.of("type", "emoji", "emoji", emoji)
                )
        );
    }

    private Map<String, Object> divider() {
        return Map.of("object", "block", "type", "divider", "divider", Map.of());
    }

    // ── 유틸 ────────────────────────────────────────────────────────────

    private List<String> splitText(String text, int maxLen) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isBlank()) return parts;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }

    private void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("NOTION_API_KEY가 설정되지 않았습니다");
        }
        if (parentPageId == null || parentPageId.isBlank()) {
            throw new IllegalStateException("NOTION_PARENT_PAGE_ID가 설정되지 않았습니다");
        }
    }
}
