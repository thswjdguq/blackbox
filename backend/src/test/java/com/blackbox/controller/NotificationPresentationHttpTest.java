package com.blackbox.controller;

import com.blackbox.entity.*;
import com.blackbox.repository.UserNotificationRepository;
import com.blackbox.scheduler.ScoreScheduler;
import com.blackbox.security.JwtService;
import com.blackbox.service.NotificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 실제 PostgreSQL의 기존 알림을 인증 필터와 JSON 직렬화를 거쳐 K-41 추가 필드로 읽는다. */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class NotificationPresentationHttpTest {
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired NotificationService notifications;
    @Autowired UserNotificationRepository repository;
    @MockitoBean ScoreScheduler scheduler;
    private final ObjectMapper json = new ObjectMapper();

    private record Team(Project project, User leader, User worker, User observer, User outsider) {}

    @Test void completionRequestSerializesRequiredFieldsAndKeepsExistingFields() throws Exception {
        Team t = team(); Task task = task(t);
        task.setStatus("IN_PROGRESS"); task.setCompletionRequestedBy(t.leader());
        task.setCompletionRequestedAt(OffsetDateTime.now()); em.flush();
        notifications.completionRequested(task, t.leader(), List.of(t.worker()));
        JsonNode page = page(t, t.worker(), "");
        assertEquals(1, page.path("unreadCount").asLong());
        assertTrue(page.has("nextCursor") && page.path("nextCursor").isNull());
        JsonNode item = page.path("items").get(0);
        assertEquals("TASK_COMPLETION_REQUESTED", item.path("type").asString());
        assertEquals(task.getId().toString(), item.path("taskId").asString());
        assertEquals("합성 자료 정리", item.path("taskTitle").asString());
        assertEquals("합성 팀장", item.path("actorName").asString());
        assertTrue(item.path("targetAvailable").asBoolean());
        assertTrue(item.has("readAt") && item.get("readAt").isNull());
        assertFalse(item.path("createdAt").asString().isBlank());
        assertEquals("합성 팀장님이 ‘합성 자료 정리’ 업무의 결과 확인을 요청했습니다.", item.path("message").asString());
        assertEquals("TASK", item.at("/target/kind").asString());
        assertEquals(task.getId().toString(), item.at("/target/taskId").asString());
        assertEquals("/projects/" + t.project().getId() + "/board?task=" + task.getId(), item.path("path").asString());
        nullTargetFields(item, "deliverableId", "reviewId", "messageId", "commentId", "fileId");
    }

    @Test void taskMessageAndFilePositionsSurviveTheCommonTarget() throws Exception {
        Team t = team(); Task task = task(t);
        TaskMessage message = new TaskMessage(); message.setProject(t.project()); message.setTask(task);
        message.setAuthor(t.leader()); message.setRecipient(t.worker()); message.setKind("REQUEST");
        message.setBody("합성 의견 요청"); message.setCreatedAt(OffsetDateTime.now()); em.persist(message);
        FileVault file = file(em, t.project(), t.leader(), "합성 조사.txt", 2, 'p'); em.flush();
        notifications.opinionRequested(message); notifications.fileUploaded(task, file, t.leader());
        JsonNode items = page(t, t.worker(), "").path("items");
        assertEquals(2, items.size());
        JsonNode opinion = byType(items, "TASK_OPINION_REQUESTED"), upload = byType(items, "TASK_FILE_UPLOADED");
        assertEquals(message.getId().toString(), opinion.path("messageId").asString());
        assertEquals(message.getId().toString(), opinion.at("/target/messageId").asString());
        assertEquals(file.getId().toString(), upload.path("fileId").asString());
        assertEquals(file.getId().toString(), upload.at("/target/fileId").asString());
        assertEquals("합성 조사.txt", upload.path("fileName").asString());
        assertEquals(2, upload.path("fileVersion").asInt());
        assertEquals("합성 팀장님이 ‘합성 자료 정리’ 업무에 결과 파일을 등록했습니다.", upload.path("message").asString());
        assertEquals(0, page(t, t.observer(), "").path("items").size(), "관찰자에게 새 업무 알림을 발행하지 않는다");
    }

    @Test void existingReviewFeedbackSerializesReviewTabAndSingleCommentAnchor() throws Exception {
        Team t = team(); Deliverable d = deliverable(em, t.project(), "합성 발표자료");
        ReviewRound review = round(em, d, 1, t.leader(), null);
        ReviewComment comment = new ReviewComment(); comment.setProject(t.project()); comment.setDeliverable(d);
        comment.setReview(review); comment.setAuthor(t.leader()); comment.setContent("합성 검토 의견"); em.persist(comment); em.flush();
        notifications.reviewEvent("REVIEW_FEEDBACK_RECEIVED", "HTTP-REVIEW:" + comment.getId(), review,
                comment.getId(), t.leader(), List.of(t.worker()));
        JsonNode item = page(t, t.worker(), "").path("items").get(0);
        assertEquals("DELIVERABLE_REVIEW", item.at("/target/kind").asString());
        assertEquals(d.getId().toString(), item.path("deliverableId").asString());
        assertEquals("합성 발표자료", item.path("deliverableTitle").asString());
        assertEquals(d.getId().toString(), item.at("/target/deliverableId").asString());
        assertEquals(review.getId().toString(), item.at("/target/reviewId").asString());
        assertEquals(comment.getId().toString(), item.at("/target/commentId").asString());
        assertEquals("/projects/" + t.project().getId() + "/deliverables/" + d.getId()
                + "?tab=review#comment-" + comment.getId(), item.path("path").asString());
        assertEquals("합성 팀장님이 ‘합성 발표자료’ 최종 제출물에 피드백을 남겼습니다.", item.path("message").asString());
        assertTrue(item.path("targetAvailable").asBoolean());
        nullTargetFields(item, "taskId", "messageId", "fileId");
    }

    @Test void missingAndCrossProjectTargetsRemainUnavailableWithoutLosingSnapshot() throws Exception {
        Team t = team(); Project other = project(em, t.leader());
        Task foreign = new Task(); foreign.setProject(other); foreign.setCreatedBy(t.leader());
        foreign.setTitle("다른 프로젝트 업무"); foreign.setStatus("TODO"); foreign.setPriority("MEDIUM"); em.persist(foreign); em.flush();
        for (UUID taskId : List.of(UUID.randomUUID(), foreign.getId())) {
            repository.insertIgnoringDuplicate(t.project().getId(), t.worker().getId(), t.leader().getId(), "합성 팀장",
                    "TASK_ASSIGNED", "HTTP-STALE:" + taskId, taskId, "저장된 업무 이름", null, null, null, null, OffsetDateTime.now());
        }
        JsonNode items = page(t, t.worker(), "").path("items"); assertEquals(2, items.size());
        for (JsonNode item : items) {
            assertFalse(item.path("targetAvailable").asBoolean(), "대상이 없거나 다른 프로젝트이면 이동할 수 없다");
            assertEquals("저장된 업무 이름", item.path("taskTitle").asString());
            assertEquals("합성 팀장님이 ‘저장된 업무 이름’ 업무를 배정했습니다.", item.path("message").asString());
            assertEquals(item.path("taskId"), item.at("/target/taskId"));
        }
    }

    @Test void recipientIsolationPaginationAndReadStateKeepTheirExistingContract() throws Exception {
        Team t = team(); Task task = task(t); em.flush();
        notifications.fileUploaded(task, file(em, t.project(), t.leader(), "합성 첫 파일.txt", 1, 'q'), t.leader());
        notifications.fileUploaded(task, file(em, t.project(), t.leader(), "합성 둘째 파일.txt", 1, 'r'), t.leader());
        JsonNode first = page(t, t.worker(), "?limit=1");
        assertEquals(1, first.path("items").size()); assertEquals(2, first.path("unreadCount").asLong());
        String cursor = first.path("nextCursor").asString(); assertFalse(cursor.isBlank());
        JsonNode second = page(t, t.worker(), "?limit=1&cursor=" + cursor);
        assertEquals(1, second.path("items").size()); assertTrue(second.path("nextCursor").isNull());
        String id = first.at("/items/0/id").asString();
        assertNotEquals(id, second.at("/items/0/id").asString());
        assertEquals(0, page(t, t.leader(), "").path("items").size(), "같은 팀이어도 다른 사람의 알림은 보이지 않는다");
        assertEquals(401, mvc.perform(get(path(t))).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(get(path(t)).header("Authorization", token(t.outsider()))).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(put(path(t) + "/" + id + "/read").header("Authorization", token(t.leader())))
                .andReturn().getResponse().getStatus());
        var read = mvc.perform(put(path(t) + "/" + id + "/read").header("Authorization", token(t.worker())))
                .andReturn().getResponse(); assertEquals(200, read.getStatus());
        JsonNode readAgain = json.readTree(mvc.perform(put(path(t) + "/" + id + "/read")
                .header("Authorization", token(t.worker()))).andReturn().getResponse().getContentAsByteArray());
        assertEquals(json.readTree(read.getContentAsByteArray()).path("readAt"), readAgain.path("readAt"));
        assertEquals(1, page(t, t.worker(), "?unreadOnly=true").path("items").size());
        assertEquals(1, page(t, t.worker(), "").path("unreadCount").asLong());
    }

    private Team team() {
        User leader = user(em, "합성 팀장"), worker = user(em, "합성 담당자");
        User observer = user(em, "합성 관찰자"), outsider = user(em, "합성 외부인");
        Project p = project(em, leader); member(em, p, leader, "LEADER"); member(em, p, worker, "MEMBER"); member(em, p, observer, "OBSERVER");
        return new Team(p, leader, worker, observer, outsider);
    }
    private Task task(Team t) {
        Task task = new Task(); task.setProject(t.project()); task.setCreatedBy(t.leader()); task.setTitle("합성 자료 정리");
        task.setStatus("TODO"); task.setPriority("MEDIUM"); em.persist(task); return task;
    }
    private String path(Team t) { return "/api/projects/" + t.project().getId() + "/notifications"; }
    private String token(User actor) { return "Bearer " + jwt.generateAccessToken(actor.getEmail()); }
    private JsonNode page(Team t, User actor, String query) throws Exception {
        em.flush(); em.clear();
        var response = mvc.perform(get(path(t) + query).header("Authorization", token(actor))).andReturn().getResponse();
        assertEquals(200, response.getStatus()); return json.readTree(response.getContentAsByteArray());
    }
    private JsonNode byType(JsonNode items, String type) {
        for (JsonNode item : items) if (type.equals(item.path("type").asString())) return item;
        throw new AssertionError("해당 종류의 알림이 없습니다: " + type);
    }
    private void nullTargetFields(JsonNode item, String... fields) {
        for (String field : fields) assertTrue(item.path("target").has(field) && item.at("/target/" + field).isNull(), field);
    }
}
