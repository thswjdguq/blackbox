package com.blackbox.service;

import com.blackbox.dto.NotificationPresentation.Kind;
import com.blackbox.entity.UserNotification;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NotificationPresentationFactoryTest {
    private final NotificationPresentationFactory factory = new NotificationPresentationFactory();

    @Test void currentTaskTypesShareOnePresentationBoundary() {
        for (var type : List.of("TASK_ASSIGNED", "TASK_FILE_UPLOADED", "TASK_COMPLETION_REQUESTED",
                "TASK_REVISION_REQUESTED", "TASK_COMMENT_POSTED", "TASK_OPINION_REQUESTED", "TASK_OPINION_REPLIED")) {
            var n = task(type); var p = factory.create(n);
            assertEquals(Kind.TASK, p.target().kind(), type);
            assertEquals(n.getTaskId(), p.target().taskId());
            assertTrue(p.message().contains("김민지님이 ‘자료 정리’"));
            assertNotEquals("새 소식", p.label());
            assertEquals("/projects/" + n.getProjectId() + "/board?task=" + n.getTaskId(), p.path());
        }
    }

    @Test void reviewTypesUseDeliverableTitleAndPreserveCommentAnchor() {
        for (var type : List.of("REVIEW_REQUESTED", "REVIEW_FEEDBACK_RECEIVED", "REVIEW_FEEDBACK_RESOLVED",
                "REVIEW_FEEDBACK_REOPENED", "REVIEW_VERSION_POSTED")) {
            var n = review(type); n.setCommentId(UUID.randomUUID()); n.setFileId(UUID.randomUUID());
            var p = factory.create(n);
            assertEquals(Kind.DELIVERABLE_REVIEW, p.target().kind());
            assertEquals(n.getReviewId(), p.target().reviewId()); assertEquals(n.getCommentId(), p.target().commentId());
            assertEquals(n.getFileId(), p.target().fileId()); assertNull(p.target().taskId());
            assertTrue(p.message().contains("최종 보고서"));
            assertEquals("/projects/" + n.getProjectId() + "/deliverables/" + n.getDeliverableId()
                    + "?tab=review#comment-" + n.getCommentId(), p.path());
        }
    }

    @Test void taskMessageAndFilePositionSurviveWithoutTurningIntoReviewIds() {
        var n = task("TASK_COMMENT_POSTED"); n.setMessageId(UUID.randomUUID()); n.setFileId(UUID.randomUUID());
        var p = factory.create(n);
        assertEquals(n.getMessageId(), p.target().messageId()); assertEquals(n.getFileId(), p.target().fileId());
        assertNull(p.target().deliverableId()); assertNull(p.target().reviewId()); assertNull(p.target().commentId());
    }

    @Test void unknownTypesKeepValidTargetsWithGenericWording() {
        var task = task("FUTURE_TYPE"); var p = factory.create(task);
        assertEquals("새 소식", p.label()); assertEquals(Kind.TASK, p.target().kind()); assertNotNull(p.path());
        assertEquals("‘자료 정리’ 관련 새 소식이 있습니다.", p.message());
        var review = review("FUTURE_REVIEW");
        assertEquals(Kind.DELIVERABLE_REVIEW, factory.create(review).target().kind());
        task.setType(null); assertNotNull(factory.create(task).path());
    }

    @Test void missingOrConflictingTargetShapesDoNotProduceLinks() {
        var n = task("TASK_COMPLETION_REQUESTED"); n.setProjectId(null);
        assertEquals(Kind.UNKNOWN, factory.create(n).target().kind()); assertNull(factory.create(n).path());
        n = review("REVIEW_REQUESTED"); n.setReviewId(null); assertNull(factory.create(n).path());
        n = task("TASK_COMPLETION_REQUESTED"); n.setDeliverableId(UUID.randomUUID()); assertNull(factory.create(n).path());
        n = review("TASK_COMPLETION_REQUESTED"); assertNull(factory.create(n).path());
        assertNull(factory.create(new UserNotification()).path());
    }

    @Test void mailUsesTheExactCommonSentenceAndPath() {
        for (var n : List.of(task("TASK_COMPLETION_REQUESTED"), review("REVIEW_REQUESTED"))) {
            var p = factory.create(n);
            var mail = new EmailNotificationComposer(URI.create("https://team.example"), factory).compose(n);
            assertTrue(mail.text().startsWith(p.message() + "\n"));
            assertEquals("[Team Blackbox] " + p.label() + " · " + p.title(), mail.subject());
            assertEquals("https://team.example" + p.path(), mail.target().toString());
            assertEquals("EmailNotificationContent[redacted]", mail.toString());
        }
    }

    @Test void commonTextHasSafeLineBreaksAndUnicodeBoundariesWithoutMutatingSnapshot() {
        var n = task("TASK_COMPLETION_REQUESTED");
        var raw = "😀".repeat(125) + "\r\n붙임";
        n.setTaskTitle(raw); n.setActorName("김\n민지"); var p = factory.create(n);
        assertEquals("😀".repeat(120) + "…", p.title());
        assertFalse(p.message().contains("\n")); assertTrue(p.message().contains("김 민지"));
        assertEquals(raw, n.getTaskTitle()); assertEquals("김\n민지", n.getActorName());
        assertEquals("NotificationPresentation[redacted]", p.toString());
    }

    @Test void missingDisplayTextHasNeutralFallback() {
        var n = task("TASK_COMPLETION_REQUESTED"); n.setTaskTitle("\n\t "); n.setActorName(null);
        var p = factory.create(n);
        assertEquals("제목 없음", p.title()); assertTrue(p.message().startsWith("팀원님이"));
    }

    private UserNotification task(String type) {
        var n = new UserNotification(); n.setType(type); n.setProjectId(UUID.randomUUID()); n.setTaskId(UUID.randomUUID());
        n.setTaskTitle("자료 정리"); n.setActorName("김민지"); return n;
    }

    private UserNotification review(String type) {
        var n = task(type); n.setTaskId(null); n.setDeliverableId(UUID.randomUUID()); n.setReviewId(UUID.randomUUID());
        n.setTaskTitle("최종 보고서"); return n;
    }
}
