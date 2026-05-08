package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.DiscordSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DiscordNotificationService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // Discord embed color codes
    private static final int COLOR_GREEN  = 0x57F287; // 태스크 완료
    private static final int COLOR_BLUE   = 0x5865F2; // 회의 생성
    private static final int COLOR_YELLOW = 0xFEE75C; // 담당자 배정
    private static final int COLOR_RED    = 0xED4245; // 회의 10분 전
    private static final int COLOR_ORANGE = 0xE67E22; // 경보

    private final DiscordSettingsRepository settingsRepo;
    private final WebClient webClient;

    public DiscordNotificationService(DiscordSettingsRepository settingsRepo) {
        this.settingsRepo = settingsRepo;
        this.webClient    = WebClient.builder().build();
    }

    // ── ① 태스크 담당자 배정 ─────────────────────────────────────────────────

    public void notifyTaskAssigned(Task task, List<User> assignees, Project project) {
        if (assignees.isEmpty()) return;
        withSettings(project.getId(), settings -> {
            if (!settings.isNotifyTaskAssign()) return;
            String names   = assignees.stream().map(User::getName).reduce((a, b) -> a + ", " + b).orElse("");
            String dueDate = task.getDueDate() != null ? task.getDueDate().toString() : "없음";
            post(settings.getWebhookUrl(), embed(
                "✅ 담당자 배정",
                "**" + esc(task.getTitle()) + "**\n" +
                "담당자: " + names + "님\n" +
                "마감일: " + dueDate + "\n" +
                "프로젝트: " + esc(project.getName()),
                COLOR_YELLOW
            ));
        });
    }

    // ── ② 태스크 완료 ────────────────────────────────────────────────────────

    public void notifyTaskCompleted(Task task, User completedBy, Project project) {
        withSettings(project.getId(), settings -> {
            if (!settings.isNotifyTaskDone()) return;
            post(settings.getWebhookUrl(), embed(
                "🎉 태스크 완료!",
                "**" + esc(task.getTitle()) + "**\n" +
                "완료자: " + esc(completedBy.getName()) + "님\n" +
                "프로젝트: " + esc(project.getName()),
                COLOR_GREEN
            ));
        });
    }

    // ── ③ 회의 생성 ──────────────────────────────────────────────────────────

    public void notifyMeetingCreated(Meeting meeting, List<String> attendeeNames, Project project) {
        withSettings(project.getId(), settings -> {
            if (!settings.isNotifyMeetingCreate()) return;
            String dateStr    = meeting.getMeetingDate() != null
                    ? meeting.getMeetingDate().format(DATE_FMT) : "미정";
            String attendees  = attendeeNames.isEmpty() ? "없음"
                    : String.join(", ", attendeeNames);
            post(settings.getWebhookUrl(), embed(
                "📅 새 회의가 생성됐습니다",
                "제목: **" + esc(meeting.getTitle()) + "**\n" +
                "일시: " + dateStr + "\n" +
                "참석자: " + attendees + "\n" +
                "체크인 코드: `" + meeting.getCheckinCode() + "`",
                COLOR_BLUE
            ));
        });
    }

    // ── ④ 회의 10분 전 알림 ──────────────────────────────────────────────────

    public void notifyMeetingReminder(Meeting meeting, Project project, String frontendBaseUrl) {
        withSettings(project.getId(), settings -> {
            if (!settings.isNotifyMeetingReminder()) return;
            String url = frontendBaseUrl.isBlank()
                    ? "(앱에서 확인)"
                    : frontendBaseUrl + "/projects/" + project.getId() + "/meetings/" + meeting.getId();
            post(settings.getWebhookUrl(), embed(
                "⏰ 10분 후 회의 시작!",
                "제목: **" + esc(meeting.getTitle()) + "**\n" +
                "체크인 코드: `" + meeting.getCheckinCode() + "`\n" +
                "지금 접속하세요 → " + url,
                COLOR_RED
            ));
        });
    }

    // ── ⑤ 경보 발생 ──────────────────────────────────────────────────────────

    public void notifyAlert(Alert alert, Project project) {
        withSettings(project.getId(), settings -> {
            if (!settings.isNotifyAlert()) return;
            String target = alert.getUser() != null ? alert.getUser().getName() + "님" : "팀 전체";
            post(settings.getWebhookUrl(), embed(
                "⚠️ 팀 경보 발생",
                "유형: **" + alert.getAlertType() + "**\n" +
                "대상: " + target + "\n" +
                "프로젝트: " + esc(project.getName()) + "\n" +
                "기여도 분석 페이지를 확인하세요",
                COLOR_ORANGE
            ));
        });
    }

    // ── 연결 테스트 (컨트롤러용) ────────────────────────────────────────────

    public boolean test(String webhookUrl) {
        if (isBlank(webhookUrl)) return false;
        try {
            webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(embed("🔔 Blackbox 연결 테스트",
                        "Discord 알림이 정상적으로 연결되었습니다!", COLOR_GREEN))
                .retrieve()
                .toBodilessEntity()
                .block();
            return true;
        } catch (Exception e) {
            log.warn("Discord 테스트 실패: {}", e.getMessage());
            return false;
        }
    }

    // ── internal ─────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface SettingsConsumer {
        void accept(DiscordSettings settings);
    }

    private void withSettings(UUID projectId, SettingsConsumer consumer) {
        settingsRepo.findById(projectId).ifPresent(settings -> {
            if (isBlank(settings.getWebhookUrl())) return;
            try {
                consumer.accept(settings);
            } catch (Exception e) {
                log.warn("Discord 알림 처리 중 예외 (project={}): {}", projectId, e.getMessage());
            }
        });
    }

    private void post(String webhookUrl, Map<String, Object> body) {
        try {
            webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                    res -> {},
                    err -> log.warn("Discord Webhook 전송 실패: {}", err.getMessage())
                );
        } catch (Exception e) {
            log.warn("Discord Webhook 호출 예외: {}", e.getMessage());
        }
    }

    private Map<String, Object> embed(String title, String description, int color) {
        return Map.of("embeds", List.of(Map.of(
            "title",       title,
            "description", description,
            "color",       color,
            "footer",      Map.of("text", "Blackbox 알림")
        )));
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private String esc(String s) { return s == null ? "" : s; }
}
