package com.blackbox.service;

import com.blackbox.entity.Meeting;
import com.blackbox.entity.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final String    calendarDbId;  // optional — Notion 캘린더 DB ID

    public NotionService(
            @Value("${external.notion.base-url}") String baseUrl,
            @Value("${external.notion.api-key}")  String apiKey,
            @Value("${external.notion.parent-page-id}") String parentPageId,
            @Value("${external.notion.calendar-db-id:}") String calendarDbId) {
        this.apiKey       = apiKey;
        this.parentPageId = parentPageId;
        this.calendarDbId = calendarDbId;
        this.webClient    = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Notion-Version", "2022-06-28")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ── 공개 API ─────────────────────────────────────────────────────────

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && parentPageId != null && !parentPageId.isBlank();
    }

    public boolean isCalendarConfigured() {
        return isConfigured() && calendarDbId != null && !calendarDbId.isBlank();
    }

    /**
     * 회의를 Notion 캘린더 데이터베이스에 추가합니다.
     * NOTION_CALENDAR_DB_ID 가 설정된 경우에만 동작하며,
     * 미설정 시에는 일반 페이지로 내보냅니다.
     */
    public String syncMeetingToCalendar(Meeting meeting) {
        validateConfig();

        String title = meeting.getTitle() != null ? meeting.getTitle() : "회의";

        if (isCalendarConfigured()) {
            // ── 캘린더 DB 방식 (Date 속성 포함) ──────────────────────────────
            String dateIso = meeting.getMeetingDate() != null
                    ? meeting.getMeetingDate().toLocalDate().toString() : null;

            Map<String, Object> properties = new java.util.HashMap<>();
            properties.put("Name", Map.of("title", List.of(
                    Map.of("text", Map.of("content", title))
            )));
            if (dateIso != null) {
                properties.put("Date", Map.of("date", Map.of("start", dateIso)));
            }
            if (meeting.getPurpose() != null && !meeting.getPurpose().isBlank()) {
                properties.put("안건", Map.of("rich_text", List.of(
                        Map.of("text", Map.of("content", meeting.getPurpose()))
                )));
            }

            Map<String, Object> body = Map.of(
                    "parent",     Map.of("type", "database_id", "database_id", calendarDbId),
                    "properties", properties
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
            String pageId = (String) response.get("id");
            if (pageId == null) throw new RuntimeException("Notion 페이지 ID 없음");
            return "https://www.notion.so/" + pageId.replace("-", "");

        } else {
            // ── 일반 페이지 방식 (캘린더 DB 없을 때 폴백) ────────────────────
            String date = meeting.getMeetingDate() != null
                    ? meeting.getMeetingDate().format(DATE_FMT) : "날짜 미정";

            List<Map<String, Object>> children = new ArrayList<>();
            children.add(callout("🗓 " + date, "📅"));
            if (meeting.getPurpose() != null && !meeting.getPurpose().isBlank()) {
                children.add(paragraph("📌 안건: " + meeting.getPurpose()));
            }
            children.add(paragraph("📎 Team Blackbox에서 자동 생성된 회의 일정입니다."));

            Map<String, Object> body = Map.of(
                    "parent",     Map.of("type", "page_id", "page_id", parentPageId),
                    "properties", Map.of("title", Map.of("title", List.of(
                            Map.of("text", Map.of("content", "📅 " + title + " — " + date))
                    ))),
                    "children",   children
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
            String pageId = (String) response.get("id");
            if (pageId == null) throw new RuntimeException("Notion 페이지 ID 없음");
            return "https://www.notion.so/" + pageId.replace("-", "");
        }
    }

    /**
     * Hash Vault 파일 업로드 시 Notion 페이지 자동 생성.
     */
    public String syncFileEntry(String projectName, String fileName, String fileHash,
                                String uploaderName, int version, long fileSize,
                                java.time.OffsetDateTime uploadedAt) {
        validateConfig();

        String title = "📎 Hash Vault — " + fileName + " (v" + version + ")";

        List<Map<String, Object>> children = new ArrayList<>();
        children.add(heading2("📁 파일 정보"));
        children.add(callout("프로젝트: " + projectName, "📂"));
        children.add(paragraph("파일명:     " + fileName));
        children.add(paragraph("버전:       v" + version));
        children.add(paragraph("크기:       " + formatBytes(fileSize)));
        children.add(paragraph("업로더:     " + uploaderName));
        children.add(paragraph("업로드 일시: " + (uploadedAt != null
                ? uploadedAt.format(DATE_FMT) : "-")));
        children.add(divider());
        children.add(heading2("🔐 SHA-256 무결성 해시"));
        children.add(callout(fileHash, "🔒"));
        children.add(divider());
        children.add(paragraph("📎 이 페이지는 Team Blackbox Hash Vault에서 자동 생성되었습니다."));

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
        String pageId = (String) response.get("id");
        if (pageId == null) throw new RuntimeException("Notion 페이지 ID 없음");
        return "https://www.notion.so/" + pageId.replace("-", "");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 칸반 보드 태스크를 Notion 페이지로 내보냅니다.
     *
     * @param projectName 프로젝트 이름
     * @param tasks       태스크 목록
     * @param assigneeMap 태스크 ID → 담당자 이름 목록
     * @return 생성된 Notion 페이지 URL
     */
    public String syncKanban(String projectName,
                             List<Task> tasks,
                             Map<UUID, List<String>> assigneeMap) {
        validateConfig();

        String title = "칸반 보드 — " + projectName
                + " (" + java.time.LocalDate.now() + " 스냅샷)";

        List<Map<String, Object>> children = buildKanbanBlocks(tasks, assigneeMap);

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

        String pageId = (String) response.get("id");
        if (pageId == null) throw new RuntimeException("Notion 페이지 ID 없음");

        return "https://www.notion.so/" + pageId.replace("-", "");
    }

    private List<Map<String, Object>> buildKanbanBlocks(List<Task> tasks,
                                                         Map<UUID, List<String>> assigneeMap) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        // 요약 callout
        long todo       = tasks.stream().filter(t -> "TODO".equals(t.getStatus())).count();
        long inProgress = tasks.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
        long done       = tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        blocks.add(callout(
                String.format("전체 %d개 태스크 — 할 일 %d / 진행 중 %d / 완료 %d",
                        tasks.size(), todo, inProgress, done),
                "📊"
        ));
        blocks.add(divider());

        // 상태별로 섹션 출력
        for (String[] section : new String[][]{
                {"TODO", "📋 할 일"},
                {"IN_PROGRESS", "⚡ 진행 중"},
                {"DONE", "✅ 완료"}
        }) {
            String status = section[0];
            String header = section[1];

            List<Task> group = tasks.stream()
                    .filter(t -> status.equals(t.getStatus()))
                    .toList();

            blocks.add(heading2(header + " (" + group.size() + ")"));

            if (group.isEmpty()) {
                blocks.add(paragraph("(없음)"));
            } else {
                for (Task t : group) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(priorityEmoji(t.getPriority())).append(" ").append(t.getTitle());

                    if (t.getDueDate() != null) {
                        sb.append("  📅 ").append(t.getDueDate());
                    }
                    if (t.getTag() != null && !t.getTag().isBlank()) {
                        sb.append("  🏷 ").append(t.getTag());
                    }
                    List<String> assignees = assigneeMap.getOrDefault(t.getId(), List.of());
                    if (!assignees.isEmpty()) {
                        sb.append("  👤 ").append(String.join(", ", assignees));
                    }

                    blocks.add(bulletItem(sb.toString()));
                }
            }
            blocks.add(divider());
        }

        blocks.add(paragraph("📎 이 페이지는 Team Blackbox에서 자동 생성되었습니다."));
        return blocks;
    }

    private String priorityEmoji(String priority) {
        if (priority == null) return "•";
        return switch (priority) {
            case "URGENT" -> "🔴";
            case "HIGH"   -> "🟠";
            case "MEDIUM" -> "🟡";
            default       -> "🟢";
        };
    }

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
