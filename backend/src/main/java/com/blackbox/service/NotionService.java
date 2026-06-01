package com.blackbox.service;

import com.blackbox.entity.Meeting;
import com.blackbox.entity.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.ZoneId;
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

    private static final Logger log = LoggerFactory.getLogger(NotionService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(KST);

    private final WebClient webClient;
    private final String    parentPageId;
    private final String    apiKey;
    private final String    calendarDbId;

    public record NotionExportResult(String pageId, String pageUrl) {}

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
     * 회의록을 Notion 페이지로 내보내거나 기존 페이지를 업데이트합니다.
     * meeting.notionPageId 가 없으면 create, 있으면 upsert(update).
     */
    public NotionExportResult exportMeeting(Meeting meeting, String aiSummary) {
        validateConfig();

        String date  = meeting.getMeetingDate() != null
                ? meeting.getMeetingDate().format(DATE_FMT) : "날짜 미정";
        String title = "회의록 — " + meeting.getTitle() + " (" + date + ")";
        List<Map<String, Object>> blocks = buildBlocks(meeting, aiSummary);

        // 기존 URL 포맷(https://...)으로 저장된 구버전 데이터는 무시하고 새 페이지 생성
        String existingPageId = meeting.getNotionPageId();
        boolean hasValidPageId = existingPageId != null
                && !existingPageId.isBlank()
                && !existingPageId.startsWith("http");

        if (!hasValidPageId) {
            String pageId = createPage(title, blocks);
            return new NotionExportResult(pageId, toUrl(pageId));
        } else {
            // 서버 키로 접근 가능한지 먼저 확인 — 다른 유저 워크스페이스 페이지이면 새로 생성
            if (!isPageAccessibleWithServerKey(existingPageId)) {
                log.info("기존 Notion 페이지가 서버 키로 접근 불가 (다른 유저 워크스페이스) — 새 페이지 생성: {}", existingPageId);
                String pageId = createPage(title, blocks);
                return new NotionExportResult(pageId, toUrl(pageId));
            }
            updatePage(existingPageId, title, blocks);
            return new NotionExportResult(existingPageId, toUrl(existingPageId));
        }
    }

    /** 서버 API Key로 해당 페이지에 접근 가능한지 확인 */
    @SuppressWarnings("unchecked")
    private boolean isPageAccessibleWithServerKey(String pageId) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/v1/pages/" + pageId)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Notion 페이지를 아카이브(삭제) 처리합니다.
     * 회의록 삭제 시 호출됩니다.
     */
    public void archivePage(String pageId) {
        if (pageId == null || pageId.isBlank() || !isConfigured()) return;
        try {
            webClient.patch()
                    .uri("/v1/pages/" + pageId)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("archived", true))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            log.info("Notion 페이지 아카이브 완료: {}", pageId);
        } catch (Exception e) {
            log.warn("Notion 페이지 아카이브 실패 (pageId={}): {}", pageId, e.getMessage());
        }
    }

    /**
     * 회의를 Notion 캘린더 데이터베이스에 추가합니다.
     */
    public String syncMeetingToCalendar(Meeting meeting) {
        validateConfig();

        String title = meeting.getTitle() != null ? meeting.getTitle() : "회의";

        if (isCalendarConfigured()) {
            String dateIso = meeting.getMeetingDate() != null
                    ? meeting.getMeetingDate().atZoneSameInstant(KST).toLocalDate().toString() : null;

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
            return toUrl(pageId);

        } else {
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
            return toUrl(pageId);
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
        children.add(heading2("🔐 SHA-256 해시"));
        children.add(callout(fileHash, "🔒"));
        children.add(divider());
        children.add(paragraph("📎 이 페이지는 Team Blackbox Hash Vault에서 자동 생성되었습니다."));

        String pageId = createPage(title, children);
        return toUrl(pageId);
    }

    /**
     * 칸반 보드 태스크를 Notion 페이지로 내보냅니다.
     */
    public String syncKanban(String projectName,
                             List<Task> tasks,
                             Map<UUID, List<String>> assigneeMap) {
        validateConfig();

        String title = "칸반 보드 — " + projectName
                + " (" + java.time.LocalDate.now() + " 스냅샷)";

        List<Map<String, Object>> children = buildKanbanBlocks(tasks, assigneeMap);

        String pageId = createPage(title, children);
        return toUrl(pageId);
    }

    // ── 유저별 키 오버로딩 ────────────────────────────────────────────────

    /**
     * 유저 개인 Notion 키로 회의록을 내보냅니다.
     * 기존 exportMeeting(Meeting, String)은 그대로 유지됩니다.
     */
    public NotionExportResult exportMeeting(Meeting meeting, String aiSummary,
                                             String userApiKey, String userPageId) {
        String date  = meeting.getMeetingDate() != null
                ? meeting.getMeetingDate().format(DATE_FMT) : "날짜 미정";
        String title = "회의록 — " + meeting.getTitle() + " (" + date + ")";
        List<Map<String, Object>> blocks = buildBlocks(meeting, aiSummary);

        String existingPageId = meeting.getNotionPageId();
        boolean hasValidPageId = existingPageId != null
                && !existingPageId.isBlank()
                && !existingPageId.startsWith("http");

        if (!hasValidPageId) {
            String pageId = createPage(title, blocks, userApiKey, userPageId);
            return new NotionExportResult(pageId, toUrl(pageId));
        } else {
            updatePage(existingPageId, title, blocks, userApiKey);
            return new NotionExportResult(existingPageId, toUrl(existingPageId));
        }
    }

    /**
     * 유저 개인 키로 Notion 페이지를 아카이브합니다.
     */
    public void archivePage(String pageId, String userApiKey) {
        if (pageId == null || pageId.isBlank() || userApiKey == null || userApiKey.isBlank()) return;
        try {
            webClient.patch()
                    .uri("/v1/pages/" + pageId)
                    .header("Authorization", "Bearer " + userApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("archived", true))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            log.info("Notion 페이지 아카이브 완료 (유저 키): {}", pageId);
        } catch (Exception e) {
            log.warn("Notion 페이지 아카이브 실패 (pageId={}): {}", pageId, e.getMessage());
        }
    }

    /**
     * Notion API Key 유효성 검증 — 실제 API 호출로 워크스페이스 이름을 가져옵니다.
     * @return 워크스페이스 이름 (실패 시 null)
     */
    @SuppressWarnings("unchecked")
    public String validateAndGetWorkspaceName(String userApiKey) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/v1/users/me")
                    .header("Authorization", "Bearer " + userApiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response == null) return null;
            Map<String, Object> bot = (Map<String, Object>) response.get("bot");
            if (bot == null) return null;
            Object name = bot.get("workspace_name");
            return name instanceof String s ? s : "내 워크스페이스";
        } catch (Exception e) {
            log.warn("Notion API 유효성 검증 실패: {}", e.getMessage());
            return null;
        }
    }

    // ── 내부 Notion API 호출 ─────────────────────────────────────────────

    private String createPage(String title, List<Map<String, Object>> children,
                              String token, String parentPage) {
        Map<String, Object> body = Map.of(
                "parent",     Map.of("type", "page_id", "page_id", parentPage),
                "properties", Map.of("title", Map.of("title", List.of(
                        Map.of("text", Map.of("content", title))
                ))),
                "children", children
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient.post()
                .uri("/v1/pages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) throw new RuntimeException("Notion API 응답 없음");
        String pageId = (String) response.get("id");
        if (pageId == null) throw new RuntimeException("Notion 페이지 ID 없음");
        return pageId;
    }

    private void updatePage(String pageId, String title, List<Map<String, Object>> newBlocks,
                            String token) {
        try {
            webClient.patch()
                    .uri("/v1/pages/" + pageId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("properties", Map.of("title", Map.of("title", List.of(
                            Map.of("text", Map.of("content", title))
                    )))))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.warn("Notion 제목 업데이트 실패: {}", e.getMessage());
        }

        List<String> blockIds = listAllBlockIds(pageId, token);
        for (String blockId : blockIds) {
            try {
                webClient.delete()
                        .uri("/v1/blocks/" + blockId)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
            } catch (Exception e) {
                log.warn("Notion 블록 삭제 실패 (blockId={}): {}", blockId, e.getMessage());
            }
        }

        try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        try {
            webClient.patch()
                    .uri("/v1/blocks/" + pageId + "/children")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("children", newBlocks))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.warn("Notion 블록 추가 실패: {}", e.getMessage());
            throw new RuntimeException("Notion 페이지 내용 업데이트 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> listAllBlockIds(String pageId, String token) {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        do {
            String uri = "/v1/blocks/" + pageId + "/children?page_size=100"
                    + (cursor != null ? "&start_cursor=" + cursor : "");
            try {
                Map<String, Object> response = webClient.get()
                        .uri(uri)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                if (response == null) break;
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                if (results != null) {
                    results.stream().map(b -> (String) b.get("id")).filter(id -> id != null).forEach(ids::add);
                }
                Boolean hasMore = (Boolean) response.get("has_more");
                cursor = (hasMore != null && hasMore) ? (String) response.get("next_cursor") : null;
            } catch (Exception e) {
                log.warn("Notion 블록 목록 조회 실패: {}", e.getMessage());
                break;
            }
        } while (cursor != null);
        return ids;
    }

    private String createPage(String title, List<Map<String, Object>> children) {
        Map<String, Object> body = Map.of(
                "parent",     Map.of("type", "page_id", "page_id", parentPageId),
                "properties", Map.of("title", Map.of("title", List.of(
                        Map.of("text", Map.of("content", title))
                ))),
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
        return pageId;
    }

    private void updatePage(String pageId, String title, List<Map<String, Object>> newBlocks) {
        // 1. 제목 업데이트
        try {
            webClient.patch()
                    .uri("/v1/pages/" + pageId)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("properties", Map.of("title", Map.of("title", List.of(
                            Map.of("text", Map.of("content", title))
                    )))))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.warn("Notion 제목 업데이트 실패: {}", e.getMessage());
        }

        // 2. 기존 블록 전체 삭제
        List<String> blockIds = listAllBlockIds(pageId);
        for (String blockId : blockIds) {
            try {
                webClient.delete()
                        .uri("/v1/blocks/" + blockId)
                        .header("Authorization", "Bearer " + apiKey)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
            } catch (Exception e) {
                log.warn("Notion 블록 삭제 실패 (blockId={}): {}", blockId, e.getMessage());
            }
        }

        // 3. rate limit 방지 딜레이
        try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        // 4. 새 블록 추가
        try {
            webClient.patch()
                    .uri("/v1/blocks/" + pageId + "/children")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("children", newBlocks))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.warn("Notion 블록 추가 실패: {}", e.getMessage());
            throw new RuntimeException("Notion 페이지 내용 업데이트 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> listAllBlockIds(String pageId) {
        List<String> ids = new ArrayList<>();
        String cursor = null;

        do {
            String uri = "/v1/blocks/" + pageId + "/children?page_size=100"
                    + (cursor != null ? "&start_cursor=" + cursor : "");
            try {
                Map<String, Object> response = webClient.get()
                        .uri(uri)
                        .header("Authorization", "Bearer " + apiKey)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (response == null) break;

                List<Map<String, Object>> results =
                        (List<Map<String, Object>>) response.get("results");
                if (results != null) {
                    results.stream()
                            .map(b -> (String) b.get("id"))
                            .filter(id -> id != null)
                            .forEach(ids::add);
                }

                Boolean hasMore = (Boolean) response.get("has_more");
                cursor = (hasMore != null && hasMore) ? (String) response.get("next_cursor") : null;
            } catch (Exception e) {
                log.warn("Notion 블록 목록 조회 실패: {}", e.getMessage());
                break;
            }
        } while (cursor != null);

        return ids;
    }

    private String toUrl(String pageId) {
        return "https://www.notion.so/" + pageId.replace("-", "");
    }

    // ── 회의록 블록 빌더 ─────────────────────────────────────────────────

    private List<Map<String, Object>> buildBlocks(Meeting meeting, String aiSummary) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        blocks.add(heading2("📋 회의 기본 정보"));
        blocks.add(callout("🗓  " + (meeting.getMeetingDate() != null
                ? meeting.getMeetingDate().format(DATE_FMT) : "날짜 미정"), "📅"));

        if (meeting.getPurpose() != null && !meeting.getPurpose().isBlank()) {
            blocks.add(paragraph("📌 안건: " + meeting.getPurpose()));
        }

        blocks.add(divider());
        blocks.add(heading2("📝 회의 내용"));
        if (meeting.getNotes() != null && !meeting.getNotes().isBlank()) {
            for (String chunk : splitText(meeting.getNotes(), 1900)) {
                blocks.add(paragraph(chunk));
            }
        } else {
            blocks.add(paragraph("(회의 내용 없음)"));
        }

        blocks.add(divider());
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

        if (aiSummary != null && !aiSummary.isBlank()) {
            blocks.add(divider());
            blocks.add(heading2("🤖 AI 요약 (Claude)"));
            for (String chunk : splitText(aiSummary, 1900)) {
                blocks.add(paragraph(chunk));
            }
        }

        blocks.add(divider());
        blocks.add(paragraph("📎 이 페이지는 Team Blackbox에서 자동 생성되었습니다."));

        return blocks;
    }

    // ── 칸반 블록 빌더 ──────────────────────────────────────────────────

    private List<Map<String, Object>> buildKanbanBlocks(List<Task> tasks,
                                                         Map<UUID, List<String>> assigneeMap) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        long todo       = tasks.stream().filter(t -> "TODO".equals(t.getStatus())).count();
        long inProgress = tasks.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
        long done       = tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        blocks.add(callout(
                String.format("전체 %d개 태스크 — 할 일 %d / 진행 중 %d / 완료 %d",
                        tasks.size(), todo, inProgress, done), "📊"
        ));
        blocks.add(divider());

        for (String[] section : new String[][]{
                {"TODO", "📋 할 일"},
                {"IN_PROGRESS", "⚡ 진행 중"},
                {"DONE", "✅ 완료"}
        }) {
            String status = section[0];
            String header = section[1];
            List<Task> group = tasks.stream()
                    .filter(t -> status.equals(t.getStatus())).toList();

            blocks.add(heading2(header + " (" + group.size() + ")"));

            if (group.isEmpty()) {
                blocks.add(paragraph("(없음)"));
            } else {
                for (Task t : group) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(priorityEmoji(t.getPriority())).append(" ").append(t.getTitle());
                    if (t.getDueDate() != null)
                        sb.append("  📅 ").append(t.getDueDate());
                    if (t.getTag() != null && !t.getTag().isBlank())
                        sb.append("  🏷 ").append(t.getTag());
                    List<String> assignees = assigneeMap.getOrDefault(t.getId(), List.of());
                    if (!assignees.isEmpty())
                        sb.append("  👤 ").append(String.join(", ", assignees));
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

    // ── 블록 생성 헬퍼 ──────────────────────────────────────────────────

    private Map<String, Object> heading2(String text) {
        return Map.of("object", "block", "type", "heading_2",
                "heading_2", Map.of("rich_text", List.of(
                        Map.of("type", "text", "text", Map.of("content", text)))));
    }

    private Map<String, Object> paragraph(String text) {
        return Map.of("object", "block", "type", "paragraph",
                "paragraph", Map.of("rich_text", List.of(
                        Map.of("type", "text", "text", Map.of("content", text)))));
    }

    private Map<String, Object> bulletItem(String text) {
        return Map.of("object", "block", "type", "bulleted_list_item",
                "bulleted_list_item", Map.of("rich_text", List.of(
                        Map.of("type", "text", "text", Map.of("content", text)))));
    }

    private Map<String, Object> callout(String text, String emoji) {
        return Map.of("object", "block", "type", "callout",
                "callout", Map.of(
                        "rich_text", List.of(Map.of("type", "text", "text", Map.of("content", text))),
                        "icon", Map.of("type", "emoji", "emoji", emoji)));
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

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void validateConfig() {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("NOTION_API_KEY가 설정되지 않았습니다");
        if (parentPageId == null || parentPageId.isBlank())
            throw new IllegalStateException("NOTION_PARENT_PAGE_ID가 설정되지 않았습니다");
    }
}
