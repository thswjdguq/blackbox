package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 실제 권한 필터·PostgreSQL·미해결 집계를 사용한다. 준비 자료는 테스트 트랜잭션과 함께 되돌린다. */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class TaskFinalizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired UnresolvedCommentCounter unresolved;
    User leader, member, observer, outsider;
    Project project;
    Deliverable report, other;
    Task linked, free;
    ReviewRound previous, approved;

    @BeforeEach void setup() {
        leader = user(em, "B22팀장"); member = user(em, "B22팀원");
        observer = user(em, "B22관찰자"); outsider = user(em, "B22비회원");
        project = project(em, leader);
        member(em, project, leader, "LEADER"); member(em, project, member, "MEMBER");
        member(em, project, observer, "OBSERVER");
        report = deliverable(em, project, "확정 대상"); other = deliverable(em, project, "진행 중");
        previous = round(em, report, 1, member, "CHANGES_REQUESTED");
        approved = round(em, report, 2, file(em, project, member, "B22.pdf", 1, 'a'), leader, "APPROVED");
        linked = task(report); free = task(null);
        em.flush();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void finalizationBlocksEveryTaskWriteWithoutChangingData(boolean submitted) throws Exception {
        finalizeReport(submitted);
        long activities = activities();
        String path = taskPath(linked);
        call(patch(path).content("{\"title\":\"변경\"}"), member, 409);
        call(patch(path + "/status").content("{\"status\":\"IN_PROGRESS\"}"), member, 409);
        call(put(path + "/assignees").content("{\"assigneeIds\":[]}"), member, 409);
        call(delete(path), leader, 409);
        call(patch(path).content("{\"clearDeliverable\":true}"), member, 409);
        call(patch(path).content("{\"deliverableId\":\"" + other.getId() + "\"}"), member, 409);
        call(patch(taskPath(free)).content("{\"deliverableId\":\"" + report.getId() + "\"}"), member, 409);
        call(post(base()).content("{\"title\":\"새 업무\",\"deliverableId\":\"" + report.getId() + "\"}"), member, 409);
        em.clear();
        Task unchanged = em.find(Task.class, linked.getId());
        assertEquals("원래 업무", unchanged.getTitle()); assertEquals("TODO", unchanged.getStatus());
        assertEquals(report.getId(), unchanged.getDeliverable().getId());
        assertNull(em.find(Task.class, free.getId()).getDeliverable());
        assertEquals(2L, em.createQuery("select count(t) from Task t where t.project.id=:id", Long.class)
                .setParameter("id", project.getId()).getSingleResult());
        assertEquals(activities, activities());
        call(get(path), observer, 200);
    }

    @Test void unrelatedTasksRemainEditableAndPermissionPrecedesFinalization() throws Exception {
        finalizeReport(false);
        call(patch(taskPath(free)).content("{\"title\":\"자유 업무 수정\"}"), member, 200);
        call(patch(taskPath(free)).content("{\"deliverableId\":\"" + other.getId() + "\"}"), member, 200);
        call(patch(taskPath(linked)).content("{\"title\":\"차단\"}"), observer, 403);
        call(patch(taskPath(linked)).content("{\"title\":\"차단\"}"), outsider, 403);
        call(patch(taskPath(linked)).content("{\"title\":\"차단\"}"), null, 401);
    }

    @Test void realUnresolvedCounterBlocksConfirmationUntilOldFeedbackIsResolved() throws Exception {
        // 현재 업무 완료는 상태 직접 변경이 아니라 요청자와 다른 팀원의 확인을 거친다.
        call(post(taskPath(linked) + "/completion-request"), member, 200);
        call(put(taskPath(linked) + "/completion-decision")
                .content("{\"approved\":true,\"criteriaConfirmed\":true}"), leader, 200);
        ReviewComment comment = new ReviewComment();
        comment.setProject(project); comment.setDeliverable(report); comment.setReview(previous);
        comment.setAuthor(member); comment.setContent("이전 회차의 미해결 의견");
        comment.setLinkedTask(linked); em.persist(comment); em.flush();
        assertEquals(1L, unresolved.countUnresolvedComments(project.getId(), report.getId()));
        call(put(confirmation()).content(reviewBody()), leader, 409);
        String comments = "/api/projects/" + project.getId() + "/deliverables/" + report.getId()
                + "/reviews/" + previous.getId() + "/comments";
        call(put(comments + "/" + comment.getId() + "/resolution").content("{}"), member, 200);
        assertEquals(0L, unresolved.countUnresolvedComments(project.getId(), report.getId()));
        call(put(confirmation()).content(reviewBody()), leader, 200);
        long activities = activities();
        call(delete(comments + "/" + comment.getId() + "/resolution"), member, 409);
        call(post(comments).content("{\"content\":\"확정 후 새 의견\"}"), member, 409);
        assertEquals(0L, unresolved.countUnresolvedComments(project.getId(), report.getId()));
        assertEquals(activities, activities());
    }

    private Task task(Deliverable delivery) {
        Task task = new Task(); task.setProject(project); task.setCreatedBy(member);
        task.setTitle("원래 업무"); task.setStatus("TODO"); task.setPriority("MEDIUM");
        task.setDeliverable(delivery); em.persist(task); return task;
    }
    private void finalizeReport(boolean submitted) throws Exception {
        call(put(confirmation()).content(reviewBody()), leader, 200);
        if (submitted) call(put(confirmation().replace("/confirmation", "/submission"))
                .content("{\"submittedAt\":\"" + OffsetDateTime.now().minusDays(1) + "\"}"), member, 200);
    }
    private String base() { return "/api/projects/" + project.getId() + "/tasks"; }
    private String taskPath(Task task) { return base() + "/" + task.getId(); }
    private String confirmation() { return "/api/projects/" + project.getId() + "/deliverables/" + report.getId() + "/confirmation"; }
    private String reviewBody() { return "{\"reviewId\":\"" + approved.getId() + "\"}"; }
    private long activities() {
        return em.createQuery("select count(a) from ActivityLog a where a.project.id=:id", Long.class)
                .setParameter("id", project.getId()).getSingleResult();
    }
    private void call(MockHttpServletRequestBuilder request, User user, int expected) throws Exception {
        em.flush(); em.clear();
        if (user != null) request.header("Authorization", "Bearer " + jwt.generateAccessToken(user.getEmail()));
        var response = mvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
        assertEquals(expected, response.getStatus(), response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
