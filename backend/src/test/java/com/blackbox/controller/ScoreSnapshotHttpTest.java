package com.blackbox.controller;

import com.blackbox.entity.*;
import com.blackbox.scheduler.ScoreScheduler;
import com.blackbox.security.JwtService;
import com.blackbox.service.AlertService;
import com.blackbox.service.ScoreService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 실제 PostgreSQL JSONB와 인증 필터를 통과한 응답 검사. Alert/주기 계산은 별도 후속 범위다. */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ScoreSnapshotHttpTest {
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ScoreService service;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ScoreScheduler scheduler;
    @MockitoBean AlertService alerts;
    final ObjectMapper json = new ObjectMapper();

    private record Team(Project project, User leader, User worker, User reviewer, User idle, User observer, User outsider) {}

    @Test void oldRowsKeepNullMetadataAndExistingReadWriteBoundaries() throws Exception {
        Team t = team();
        ContributionScore old = new ContributionScore(); old.setProject(t.project()); old.setUser(t.worker());
        old.setTaskParticipated(true); old.setFileParticipated(true); old.setParticipationLevel("PARTIAL");
        old.setCalculatedAt(OffsetDateTime.parse("2026-10-01T00:00:00Z")); em.persist(old); em.flush(); em.clear();
        JsonNode row = row(readScores(t, t.worker()), t.worker());
        assertEquals("PARTIAL", row.path("participationLevel").asString());
        assertTrue(row.path("taskParticipated").asBoolean());
        for (String field : new String[]{"items", "criteriaVersion", "period"}) {
            assertTrue(row.has(field) && row.get(field).isNull(), field + "는 소급해서 만들지 않는다");
        }
        assertEquals("2026-10-01T00:00:00Z", row.path("calculatedAt").asString());
        assertEquals(1, readScores(t, t.observer()).size(), "관찰자는 기존 조회가 가능하다");
        assertEquals(403, mvc.perform(post(path(t) + "/recalculate").header("Authorization", token(t.observer())))
                .andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(get(path(t)).header("Authorization", token(t.outsider())))
                .andReturn().getResponse().getStatus());
    }

    @Test void snapshotRoundTripsThroughJsonbAndDoesNotRefreshUntilRecalculated() throws Exception {
        Team t = team(); ReviewRound round = facts(t); em.flush(); em.clear();
        JsonNode calculated = recalculate(t);
        assertEquals(4, calculated.size(), "관찰자 점수는 만들지 않는다");
        JsonNode worker = row(calculated, t.worker()), reviewer = row(calculated, t.reviewer());
        assertEquals("FULL", worker.path("participationLevel").asString(), "기존 네 조건과 임계값을 유지한다");
        assertEquals("NONE", reviewer.path("participationLevel").asString(), "검토 횟수는 판정에 넣지 않는다");
        assertEquals(2, worker.at("/items/0/count").asLong());
        assertEquals(1, worker.at("/items/1/count").asLong()); assertEquals(2, worker.at("/items/1/total").asLong());
        assertEquals(2, worker.at("/items/2/count").asLong(), "같은 이름의 세 버전은 파일 하나다");
        assertEquals(1, worker.at("/items/3/count").asLong());
        assertEquals(9, reviewer.at("/items/4/count").asLong());
        assertFalse(reviewer.at("/items/4/countsTowardLevel").asBoolean());
        assertTrue(reviewer.at("/items/4/passed").asBoolean());
        assertEquals(6, reviewer.at("/items/4/details").size());
        String[] flags = {"taskParticipated", "meetingParticipated", "fileParticipated", "actionParticipated"};
        for (JsonNode score : calculated) {
            assertEquals(5, score.path("items").size());
            assertEquals("2026-11-v1", score.path("criteriaVersion").asString());
            assertEquals(score.path("calculatedAt"), score.at("/period/to"));
            assertFalse(score.at("/period/from").isNull());
            for (int i = 0; i < flags.length; i++) {
                assertEquals(score.path(flags[i]).asBoolean(), score.at("/items/" + i + "/passed").asBoolean());
                assertTrue(score.at("/items/" + i + "/countsTowardLevel").asBoolean());
            }
        }
        JsonNode stored = row(readScores(t, t.worker()), t.reviewer());
        assertEquals(reviewer, stored, "POST와 DB 재조회 응답의 값과 시각이 같다");
        assertEquals(5, ((Number) em.createNativeQuery("""
                select jsonb_array_length(items) from contribution_scores where project_id=:p and user_id=:u
                """).setParameter("p", t.project().getId()).setParameter("u", t.reviewer().getId()).getSingleResult()).intValue());
        ReviewComment more = new ReviewComment(); more.setProject(t.project()); more.setDeliverable(round.getDeliverable());
        more.setReview(round); more.setAuthor(t.reviewer()); more.setContent("합성 추가 피드백"); em.persist(more);
        em.flush(); em.clear();
        assertEquals(stored, row(readScores(t, t.worker()), t.reviewer()), "GET은 새 피드백을 즉석 집계하지 않는다");
        JsonNode updated = row(recalculate(t), t.reviewer());
        assertEquals(10, updated.at("/items/4/count").asLong());
        assertEquals("NONE", updated.path("participationLevel").asString());
        assertFalse(OffsetDateTime.parse(updated.path("calculatedAt").asString()).isBefore(
                OffsetDateTime.parse(stored.path("calculatedAt").asString())));
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void calculationJoinsCallerTransactionAndRollsBackItsWholeSnapshot() {
        var projectId = new AtomicReference<UUID>();
        var tx = new TransactionTemplate(transactions);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            Team t = team(); projectId.set(t.project().getId()); em.flush(); em.clear();
            assertEquals(4, service.recalculate(em.getReference(Project.class, projectId.get())).size());
            em.flush();
            throw new IllegalStateException("synthetic rollback");
        }));
        assertEquals("synthetic rollback", failure.getMessage());
        Long remaining = tx.execute(status -> em.createQuery(
                "select count(s) from ContributionScore s where s.project.id=:id", Long.class)
                .setParameter("id", projectId.get()).getSingleResult());
        assertNotNull(remaining); assertEquals(0L, remaining.longValue());
    }

    private Team team() {
        User leader = user(em, "합성 팀장"), worker = user(em, "합성 담당자"), reviewer = user(em, "합성 검토자");
        User idle = user(em, "합성 팀원"), observer = user(em, "합성 관찰자"), outsider = user(em, "합성 외부인");
        Project p = project(em, leader); member(em, p, leader, "LEADER"); member(em, p, worker, "MEMBER");
        member(em, p, reviewer, "MEMBER"); member(em, p, idle, "MEMBER"); member(em, p, observer, "OBSERVER");
        return new Team(p, leader, worker, reviewer, idle, observer, outsider);
    }

    private ReviewRound facts(Team t) {
        Project p = t.project();
        file(em, p, t.worker(), "합성 보고서.pdf", 1, 'a'); file(em, p, t.worker(), "합성 보고서.pdf", 2, 'b');
        file(em, p, t.worker(), "합성 보고서.pdf", 3, 'c'); file(em, p, t.worker(), "합성 발표.pdf", 1, 'd');
        Project other = project(em, t.leader()); file(em, other, t.worker(), "다른 프로젝트.pdf", 1, 'e');
        Deliverable d = deliverable(em, p, "합성 제출물");
        ReviewRound r = round(em, d, 1, t.leader(), null); r.decide("APPROVED", t.reviewer());
        ReviewComment c = new ReviewComment(); c.setProject(p); c.setDeliverable(d); c.setReview(r);
        c.setAuthor(t.reviewer()); c.setContent("합성 피드백"); c.setResolvedBy(t.worker()); c.setResolvedAt(OffsetDateTime.now()); em.persist(c);
        requirement(em, d, true, t.worker());
        Task task = doneTask(t, null); doneTask(t, "[Action item from meeting: synthetic]");
        message(task, t.reviewer(), "COMMENT", null, null); message(task, t.reviewer(), "COMMENT", null, null);
        TaskMessage request = message(task, t.worker(), "REQUEST", t.reviewer(), null);
        message(task, t.reviewer(), "REPLY", null, request);
        for (int i = 0; i < 2; i++) {
            ActivityLog log = new ActivityLog(); log.setProject(p); log.setUser(t.reviewer()); log.setSource("PLATFORM");
            log.setActionType("TASK_COMPLETION_REJECT"); log.setOccurredAt(OffsetDateTime.now()); log.setTrustLevel(BigDecimal.ONE); em.persist(log);
        }
        for (int i = 0; i < 2; i++) {
            Meeting m = new Meeting(); m.setProject(p); m.setCreatedBy(t.leader()); m.setTitle("합성 회의");
            m.setMeetingDate(OffsetDateTime.now().minusDays(i + 1)); m.setCheckinCode(UUID.randomUUID().toString().substring(0, 8)); em.persist(m);
            if (i == 0) {
                MeetingAttendee a = new MeetingAttendee(); a.setMeeting(m); a.setUser(t.worker()); a.setCheckedIn(true);
                a.setCheckedAt(OffsetDateTime.now()); a.setCheckedInDate(LocalDate.now()); a.setAttendanceStatus("PRESENT"); em.persist(a);
            }
        }
        return r;
    }

    private Task doneTask(Team t, String description) {
        Task task = new Task(); task.setProject(t.project()); task.setCreatedBy(t.leader()); task.setTitle("합성 완료 업무");
        task.setDescription(description); task.setStatus("DONE"); task.setPriority("MEDIUM"); task.setCompletedAt(OffsetDateTime.now());
        task.setCompletionApprovedBy(t.reviewer()); task.setCompletionApprovedAt(OffsetDateTime.now()); em.persist(task);
        TaskAssignee a = new TaskAssignee(); a.setTask(task); a.setUser(t.worker()); em.persist(a); return task;
    }

    private TaskMessage message(Task t, User author, String kind, User recipient, TaskMessage parent) {
        TaskMessage m = new TaskMessage(); m.setProject(t.getProject()); m.setTask(t); m.setAuthor(author); m.setKind(kind);
        m.setRecipient(recipient); m.setParent(parent); m.setBody("합성 대화"); m.setCreatedAt(OffsetDateTime.now()); em.persist(m); return m;
    }
    private String path(Team t) { return "/api/projects/" + t.project().getId() + "/scores"; }
    private String token(User user) { return "Bearer " + jwt.generateAccessToken(user.getEmail()); }
    private JsonNode row(JsonNode list, User user) {
        for (JsonNode r : list) if (r.path("userId").asString().equals(user.getId().toString())) return r;
        throw new AssertionError("사용자의 점수 응답이 없습니다");
    }
    private JsonNode readScores(Team t, User actor) throws Exception {
        var response = mvc.perform(get(path(t)).header("Authorization", token(actor))).andReturn().getResponse();
        assertEquals(200, response.getStatus()); return json.readTree(response.getContentAsByteArray());
    }
    private JsonNode recalculate(Team t) throws Exception {
        var response = mvc.perform(post(path(t) + "/recalculate").header("Authorization", token(t.leader())))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus()); JsonNode result = json.readTree(response.getContentAsByteArray());
        em.flush(); em.clear(); return result;
    }
}
