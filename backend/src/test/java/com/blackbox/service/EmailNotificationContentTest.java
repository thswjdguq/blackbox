package com.blackbox.service;

import com.blackbox.entity.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 순수 내용·정책 단위 검사. 실제 SMTP/Gmail·DB·현재 알림 경로 연결은 검사하지 않는다. */
class EmailNotificationContentTest {
    private final UUID projectId = UUID.randomUUID();
    private final UserNotification notification = notification();
    private final ProjectMember member = member();

    @Test void disabledOrEmptyAllowlistSendsNothing() {
        assertFalse(new EmailNotificationPolicy(false, List.of("peer@example.test")).permits(projectId, notification, member));
        assertFalse(new EmailNotificationPolicy(true, List.of()).permits(projectId, notification, member));
        assertFalse(new EmailNotificationPolicy(true, null).permits(projectId, notification, member));
    }

    @Test void onlyCurrentContributorOfSameProjectAndNotificationRecipientPasses() {
        var policy = new EmailNotificationPolicy(true, List.of("peer@EXAMPLE.test"));
        assertTrue(policy.permits(projectId, notification, member));
        member.setRole("LEADER"); assertTrue(policy.permits(projectId, notification, member));
        member.setRole("OBSERVER"); assertFalse(policy.permits(projectId, notification, member));
        member.setRole("MEMBER"); assertFalse(policy.permits(projectId, notification, null));
        assertFalse(policy.permits(UUID.randomUUID(), notification, member));
        member.getProject().setId(UUID.randomUUID()); assertFalse(policy.permits(projectId, notification, member));
        member.getProject().setId(projectId); member.getUser().setId(UUID.randomUUID());
        assertFalse(policy.permits(projectId, notification, member));
    }

    @Test void excludesActorAndOtherEventTypes() {
        var policy = new EmailNotificationPolicy(true, List.of("peer@example.test"));
        notification.setActorId(member.getUser().getId()); assertFalse(policy.permits(projectId, notification, member));
        notification.setActorId(UUID.randomUUID()); notification.setType("REVIEW_REQUESTED");
        assertTrue(policy.permits(projectId, notification, member));
        for (var type : List.of("TASK_COMMENT_POSTED", "REVIEW_VERSION_POSTED", "TASK_ASSIGNED", "unknown")) {
            notification.setType(type); assertFalse(policy.permits(projectId, notification, member));
        }
    }

    @Test void neverExpandsAllowlistToOtherAddressesOrHeaderSyntax() {
        var policy = new EmailNotificationPolicy(true, List.of("peer@example.test"));
        for (var email : List.of("other@example.test", "peer+other@example.test", "Peer@example.test",
                "peer@example.test\r\nBcc: other@example.test", "peer@example.test,other@example.test", "<peer@example.test>")) {
            member.getUser().setEmail(email); assertFalse(policy.permits(projectId, notification, member), email);
        }
        member.getUser().setEmail(null); assertFalse(policy.permits(projectId, notification, member));
        member.getUser().setEmail("peer@example.test");
        assertFalse(new EmailNotificationPolicy(true, List.of("<peer@example.test>")).permits(projectId, notification, member));
    }

    @Test void taskLinkHasFixedOriginAndContainsOnlyUuidTarget() {
        var content = new EmailNotificationComposer(URI.create("https://team.example/")).compose(notification);
        assertEquals("https://team.example/projects/" + projectId + "/board?task=" + notification.getTaskId(), content.target().toString());
        assertTrue(content.subject().contains("업무 결과 확인 요청"));
        assertTrue(content.text().contains("팀원합성님이"));
        assertFalse(content.text().contains("peer@example.test"));
        assertEquals("EmailNotificationContent[redacted]", content.toString());
    }

    @Test void reviewLinkOpensExistingReviewTabAndRejectsMissingIds() {
        notification.setType("REVIEW_REQUESTED"); notification.setDeliverableId(UUID.randomUUID()); notification.setReviewId(UUID.randomUUID());
        var composer = new EmailNotificationComposer(URI.create("https://team.example"));
        assertEquals("https://team.example/projects/" + projectId + "/deliverables/" + notification.getDeliverableId() + "?tab=review", composer.compose(notification).target().toString());
        notification.setReviewId(null); assertThrows(NullPointerException.class, () -> composer.compose(notification));
        notification.setType("REVIEW_VERSION_POSTED"); assertThrows(IllegalArgumentException.class, () -> composer.compose(notification));
    }

    @Test void rejectsUntrustedOrAmbiguousBaseAddresses() {
        for (var url : List.of("http://public.example", "https://team.example@evil.example/path", "https://team.example/path",
                "https://team.example?next=evil", "https://team.example#token", "//team.example", "file:///tmp", "https://team.example:0"))
            assertThrows(IllegalArgumentException.class, () -> new EmailNotificationComposer(URI.create(url)), url);
        assertDoesNotThrow(() -> new EmailNotificationComposer(URI.create("http://127.0.0.1:13000")));
        assertDoesNotThrow(() -> new EmailNotificationComposer(URI.create("http://localhost:13000/")));
    }

    @Test void stripsHeaderLineBreaksAndKeepsEmojiWholeWhenShortening() {
        notification.setTaskTitle("제목\r\nBcc: x@example.test\t" + "😀".repeat(150));
        notification.setActorName("팀원\n새 줄");
        var content = new EmailNotificationComposer(URI.create("https://team.example")).compose(notification);
        assertFalse(content.subject().contains("\r")); assertFalse(content.subject().contains("\n"));
        assertFalse(content.text().contains("팀원\n새 줄")); assertTrue(content.subject().endsWith("…"));
        for (int i = 0; i < content.subject().length(); i++) {
            char ch = content.subject().charAt(i);
            if (Character.isHighSurrogate(ch)) assertTrue(++i < content.subject().length() && Character.isLowSurrogate(content.subject().charAt(i)));
            else assertFalse(Character.isLowSurrogate(ch));
        }
    }

    private UserNotification notification() {
        var n = new UserNotification(); n.setProjectId(projectId); n.setActorId(UUID.randomUUID());
        n.setRecipientId(UUID.randomUUID()); n.setType("TASK_COMPLETION_REQUESTED"); n.setTaskId(UUID.randomUUID());
        n.setTaskTitle("합성 업무"); n.setActorName("팀원합성"); return n;
    }

    private ProjectMember member() {
        var p = new Project(); p.setId(projectId); var u = new User(); u.setId(notification.getRecipientId());
        u.setEmail("peer@example.test"); var m = new ProjectMember(); m.setProject(p); m.setUser(u); m.setRole("MEMBER"); return m;
    }
}
