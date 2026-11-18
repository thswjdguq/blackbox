package com.blackbox.service;

import com.blackbox.entity.ProjectMember;
import com.blackbox.entity.UserNotification;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** K-41 초안의 초기 테스트 수신자 제한. 현재 멤버/권한은 발송 직전에 읽어 전달한다. */
public final class EmailNotificationPolicy {
    private static final Set<String> TYPES = Set.of("TASK_COMPLETION_REQUESTED", "REVIEW_REQUESTED");
    private final boolean enabled;
    private final Set<String> allowed;

    public EmailNotificationPolicy(boolean enabled, Collection<String> allowedRecipients) {
        this.enabled = enabled;
        this.allowed = allowedRecipients == null ? Set.of() : allowedRecipients.stream()
                .map(EmailNotificationPolicy::addressKey).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /** 후보 이메일만으로 허용하지 않고 같은 프로젝트의 현재 기여자와 알림 수신자 id를 함께 대조한다. */
    public boolean permits(UUID projectId, UserNotification notification, ProjectMember currentMember) {
        if (!enabled || allowed.isEmpty() || projectId == null || notification == null || currentMember == null) return false;
        if (!projectId.equals(notification.getProjectId()) || notification.getType() == null
                || notification.getActorId() == null || !TYPES.contains(notification.getType())) return false;
        if (!ProjectAccessChecker.canContribute(currentMember) || currentMember.getProject() == null
                || !projectId.equals(currentMember.getProject().getId()) || currentMember.getUser() == null) return false;
        var recipient = currentMember.getUser();
        if (recipient.getId() == null || !recipient.getId().equals(notification.getRecipientId())
                || recipient.getId().equals(notification.getActorId())) return false;
        return allowed.contains(addressKey(recipient.getEmail()));
    }

    // SMTP 헤더의 목록/표시 이름 구문은 받지 않는다. 초기 허용 목록은 보수적인 단일 ASCII 주소만 받는다.
    // 도메인만 소문자화한다. 임의 공급자의 local-part 대소문자/점/플러스 주소를 같은 수신자로 합치지 않는다.
    private static String addressKey(String value) {
        if (value == null) return "";
        String email = value.strip();
        int at = email.indexOf('@');
        if (email.length() > 254 || at < 1 || at != email.lastIndexOf('@') || at > 64) return "";
        String local = email.substring(0, at), domain = email.substring(at + 1);
        if (!local.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+") || local.startsWith(".")
                || local.endsWith(".") || local.contains("..")
                || !domain.matches("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+")) return "";
        return local + "@" + domain.toLowerCase(Locale.ROOT);
    }
}
