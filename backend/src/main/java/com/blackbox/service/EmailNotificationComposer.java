package com.blackbox.service;

import com.blackbox.entity.UserNotification;
import java.net.URI;
import java.util.Objects;
import java.util.Set;

/** 고정된 서비스 출처의 원문 링크와 최소 문맥만 담는 UTF-8 plain-text 메일. 발송·DB 접근은 하지 않는다. */
public final class EmailNotificationComposer {
    private static final Set<String> LOCAL = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    private final URI origin;
    private final NotificationPresentationFactory presentations;

    public EmailNotificationComposer(URI frontendBaseUrl) {
        this(frontendBaseUrl, new NotificationPresentationFactory());
    }

    public EmailNotificationComposer(URI frontendBaseUrl, NotificationPresentationFactory presentations) {
        Objects.requireNonNull(frontendBaseUrl, "메일 링크의 서비스 주소가 필요합니다.");
        String scheme = frontendBaseUrl.getScheme();
        String host = frontendBaseUrl.getHost();
        if (host == null || frontendBaseUrl.getUserInfo() != null || frontendBaseUrl.getRawQuery() != null
                || frontendBaseUrl.getRawFragment() != null || frontendBaseUrl.getPort() == 0
                || frontendBaseUrl.getPort() > 65535 || !("https".equalsIgnoreCase(scheme)
                || "http".equalsIgnoreCase(scheme) && LOCAL.contains(host.toLowerCase(java.util.Locale.ROOT)))
                || !(frontendBaseUrl.getRawPath().isEmpty() || "/".equals(frontendBaseUrl.getRawPath()))) {
            throw new IllegalArgumentException("메일 링크에는 HTTPS 서비스 주소 또는 로컬 개발 주소를 지정하세요.");
        }
        this.origin = frontendBaseUrl;
        this.presentations = Objects.requireNonNull(presentations);
    }

    public record Content(String subject, String text, URI target) {
        @Override public String toString() { return "EmailNotificationContent[redacted]"; }
    }

    /** 지원하지 않는 유형/필수 id 누락은 발송 메시지를 만들지 않고 거절한다. */
    public Content compose(UserNotification notification) {
        Objects.requireNonNull(notification);
        Objects.requireNonNull(notification.getProjectId(), "프로젝트 id가 필요합니다.");
        if ("TASK_COMPLETION_REQUESTED".equals(notification.getType())) {
            Objects.requireNonNull(notification.getTaskId(), "업무 id가 필요합니다.");
        } else if ("REVIEW_REQUESTED".equals(notification.getType())) {
            Objects.requireNonNull(notification.getReviewId(), "검토 회차 id가 필요합니다.");
            Objects.requireNonNull(notification.getDeliverableId(), "최종 제출물 id가 필요합니다.");
        } else throw new IllegalArgumentException("이메일 알림 대상으로 지원하지 않는 유형입니다.");
        var presentation = presentations.create(notification);
        if (presentation.path() == null) throw new IllegalArgumentException("이메일 알림의 원문 대상을 확인할 수 없습니다.");
        URI target = origin.resolve(presentation.path());
        return new Content("[Team Blackbox] " + presentation.label() + " · " + presentation.title(),
                presentation.message() + "\n\nTeam Blackbox에 로그인한 뒤 내용을 확인해 주세요.\n" + target
                        + "\n\n이 메일은 확인 요청 안내이며, 업무 완료나 최종 승인을 뜻하지 않습니다.", target);
    }
}
