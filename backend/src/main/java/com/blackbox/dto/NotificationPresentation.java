package com.blackbox.dto;

import java.util.UUID;

/** 앱 응답과 메일이 함께 사용할 표시 결과. label/title은 메일 제목 구성에도 사용한다. */
public record NotificationPresentation(String label, String title, String message, Target target, String path) {
    public enum Kind { TASK, DELIVERABLE_REVIEW, UNKNOWN }

    public record Target(Kind kind, UUID taskId, UUID deliverableId, UUID reviewId,
                         UUID messageId, UUID commentId, UUID fileId) {}

    @Override public String toString() { return "NotificationPresentation[redacted]"; }
}
