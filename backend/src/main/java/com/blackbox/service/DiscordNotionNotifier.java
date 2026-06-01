package com.blackbox.service;

import com.blackbox.entity.Meeting;
import com.blackbox.repository.DiscordSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notion 회의록 내보내기 완료 시 Discord 웹훅으로 알림 전송.
 * 기존 DiscordNotificationService와 독립적으로 동작하는 전용 컴포넌트.
 */
@Component
public class DiscordNotionNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotionNotifier.class);
    private static final int    COLOR_NOTION   = 5814783; // #58A9BF 노션 테마 색상
    private static final ZoneId KST            = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(KST);

    private final DiscordSettingsRepository settingsRepo;
    private final WebClient                 webClient;
    private final String                    globalWebhookUrl;

    public DiscordNotionNotifier(
            DiscordSettingsRepository settingsRepo,
            @Value("${external.discord.webhook-url:}") String globalWebhookUrl) {
        this.settingsRepo     = settingsRepo;
        this.globalWebhookUrl = globalWebhookUrl;
        this.webClient        = WebClient.builder().build();
    }

    /**
     * Notion 내보내기 완료 알림.
     * @Async — 응답 속도에 영향을 주지 않도록 별도 스레드에서 실행.
     * 실패해도 예외를 던지지 않고 log.warn으로만 처리.
     */
    @Async
    public void notifyExported(Meeting meeting, String exporterName,
                               String notionUrl, UUID projectId) {
        String webhookUrl = resolveWebhookUrl(projectId);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Discord 웹훅 미설정 — Notion 내보내기 알림 스킵 (project={})", projectId);
            return;
        }

        try {
            String dateStr = meeting.getMeetingDate() != null
                    ? meeting.getMeetingDate().format(DATE_FMT) + " KST" : "날짜 미정";

            Map<String, Object> body = Map.of("embeds", List.of(Map.of(
                "title",  "📋 회의록이 Notion에 업로드되었습니다",
                "color",  COLOR_NOTION,
                "fields", List.of(
                    Map.of("name", "회의 제목", "value", safe(meeting.getTitle()), "inline", true),
                    Map.of("name", "작성자",   "value", safe(exporterName),        "inline", true),
                    Map.of("name", "날짜",     "value", dateStr,                   "inline", true),
                    Map.of("name", "Notion 링크", "value", "[페이지 열기](" + notionUrl + ")", "inline", false)
                ),
                "footer", Map.of("text", "Team Blackbox • Notion 자동 연동")
            )));

            webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                    res -> {},
                    err -> log.warn("Discord Notion 알림 전송 실패 (project={}): {}", projectId, err.getMessage())
                );
        } catch (Exception e) {
            log.warn("Discord Notion 알림 구성 중 예외 (project={}): {}", projectId, e.getMessage());
        }
    }

    // ── internal ──────────────────────────────────────────────────────────

    /** 프로젝트 DB 웹훅 우선 → 글로벌 폴백 → null */
    private String resolveWebhookUrl(UUID projectId) {
        return settingsRepo.findById(projectId)
                .map(s -> {
                    String url = s.getWebhookUrl();
                    return (url != null && !url.isBlank()) ? url : globalWebhookUrl;
                })
                .orElse(globalWebhookUrl.isBlank() ? null : globalWebhookUrl);
    }

    private String safe(String s) { return s != null ? s : ""; }
}
