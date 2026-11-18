package com.blackbox.service;

import com.blackbox.dto.NotificationPresentation;
import com.blackbox.dto.NotificationPresentation.Kind;
import com.blackbox.dto.NotificationPresentation.Target;
import com.blackbox.entity.UserNotification;
import java.util.Map;
import java.util.Objects;

/** 알림 종류별 문장과 원문 경로를 만드는 단일 경계. 저장된 스냅숏을 읽고 DB를 수정하지 않는다. */
public final class NotificationPresentationFactory {
    private record Wording(String label, String sentence, Kind kind) {}
    private static Wording task(String label, String sentence) { return new Wording(label, sentence, Kind.TASK); }
    private static Wording review(String label, String sentence) { return new Wording(label, sentence, Kind.DELIVERABLE_REVIEW); }
    private static final Map<String, Wording> WORDING = Map.ofEntries(
            Map.entry("TASK_ASSIGNED", task("업무 배정", "%s님이 ‘%s’ 업무를 배정했습니다.")),
            Map.entry("TASK_FILE_UPLOADED", task("결과 파일 등록", "%s님이 ‘%s’ 업무에 결과 파일을 등록했습니다.")),
            Map.entry("TASK_COMPLETION_REQUESTED", task("업무 결과 확인 요청", "%s님이 ‘%s’ 업무의 결과 확인을 요청했습니다.")),
            Map.entry("TASK_REVISION_REQUESTED", task("보완 요청", "%s님이 ‘%s’ 업무의 보완을 요청했습니다.")),
            Map.entry("TASK_COMMENT_POSTED", task("업무 피드백", "%s님이 ‘%s’ 업무에 의견을 남겼습니다.")),
            Map.entry("TASK_OPINION_REQUESTED", task("의견 요청", "%s님이 ‘%s’ 업무에 의견을 요청했습니다.")),
            Map.entry("TASK_OPINION_REPLIED", task("의견 답변", "%s님이 ‘%s’ 업무의 의견 요청에 답변했습니다.")),
            Map.entry("REVIEW_REQUESTED", review("최종 제출물 검토 요청", "%s님이 ‘%s’ 최종 제출물의 검토를 요청했습니다.")),
            Map.entry("REVIEW_FEEDBACK_RECEIVED", review("최종 제출물 피드백", "%s님이 ‘%s’ 최종 제출물에 피드백을 남겼습니다.")),
            Map.entry("REVIEW_FEEDBACK_RESOLVED", review("피드백 반영 기록", "%s님이 ‘%s’ 최종 제출물의 피드백 반영을 기록했습니다.")),
            Map.entry("REVIEW_FEEDBACK_REOPENED", review("피드백 다시 확인 요청", "%s님이 ‘%s’ 최종 제출물의 피드백을 다시 열었습니다.")),
            Map.entry("REVIEW_VERSION_POSTED", review("새 검토 버전 등록", "%s님이 ‘%s’ 최종 제출물의 새 검토 버전을 등록했습니다.")));

    public NotificationPresentation create(UserNotification notification) {
        Objects.requireNonNull(notification);
        var wording = notification.getType() == null ? null : WORDING.get(notification.getType());
        String title = oneLine(notification.getTaskTitle(), "제목 없음", 120);
        String actor = oneLine(notification.getActorName(), "팀원", 60);
        Kind kind = targetKind(notification);
        // 모르는 종류라도 유효한 대상은 보존한다. 알려진 종류와 대상 모양이 충돌하면 이동을 만들지 않는다.
        if (wording != null && wording.kind() != kind) kind = Kind.UNKNOWN;
        Target target;
        String path;
        if (kind == Kind.TASK) {
            target = new Target(kind, notification.getTaskId(), null, null, notification.getMessageId(), null, notification.getFileId());
            path = "/projects/" + notification.getProjectId() + "/board?task=" + notification.getTaskId();
        } else if (kind == Kind.DELIVERABLE_REVIEW) {
            target = new Target(kind, null, notification.getDeliverableId(), notification.getReviewId(), null,
                    notification.getCommentId(), notification.getFileId());
            path = "/projects/" + notification.getProjectId() + "/deliverables/" + notification.getDeliverableId() + "?tab=review"
                    + (notification.getCommentId() == null ? "" : "#comment-" + notification.getCommentId());
        } else {
            target = new Target(Kind.UNKNOWN, null, null, null, null, null, null);
            path = null;
        }
        String label = wording == null ? "새 소식" : wording.label();
        String message = wording == null ? "‘" + title + "’ 관련 새 소식이 있습니다."
                : wording.sentence().formatted(actor, title);
        return new NotificationPresentation(label, title, message, target, path);
    }

    private static Kind targetKind(UserNotification n) {
        if (n.getProjectId() == null) return Kind.UNKNOWN;
        if (n.getTaskId() != null && n.getDeliverableId() == null && n.getReviewId() == null && n.getCommentId() == null) return Kind.TASK;
        if (n.getTaskId() == null && n.getDeliverableId() != null && n.getReviewId() != null && n.getMessageId() == null) return Kind.DELIVERABLE_REVIEW;
        return Kind.UNKNOWN;
    }

    private static String oneLine(String value, String fallback, int limit) {
        if (value == null) return fallback;
        StringBuilder clean = new StringBuilder();
        value.codePoints().forEach(cp -> clean.appendCodePoint(Character.isISOControl(cp)
                || Character.isWhitespace(cp) || Character.isSpaceChar(cp) ? ' ' : cp));
        String text = clean.toString().strip().replaceAll(" +", " ");
        if (text.isEmpty()) return fallback;
        return text.codePointCount(0, text.length()) <= limit ? text
                : text.substring(0, text.offsetByCodePoints(0, limit)) + "…";
    }
}
