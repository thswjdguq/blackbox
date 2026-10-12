package com.blackbox.controller;

import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
import com.blackbox.service.UnresolvedCommentCounter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * K-20 11장의 동시 요청 조건. 요청들이 실제로 겹치도록 자료를 커밋해 두고 스레드를 한 번에 출발시킨다.
 * 테스트 트랜잭션으로 되돌릴 수 없으므로 만든 것은 끝나고 지운다.
 * 겹치는 순간은 실행마다 달라서 결과만 봐서는 잠금이 빠진 것을 놓칠 수 있다.
 * 그래서 쓰기들이 제출물 줄부터 잠그는지를 따로 확인하고(everyWriteLocksTheDeliverableRowFirst), 겨루기는 여러 번 되풀이한다.
 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class DeliverableConfirmationConcurrencyTest {
    static final int ROUNDS = 10;
    static final String BASE = "/api/projects/{projectId}/deliverables/{deliverableId}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    // 쓰지는 않는다. HTTP 테스트와 구성이 같아야 서버를 한 번만 띄운다
    @MockBean UnresolvedCommentCounter unresolved;

    final ExecutorService threads = Executors.newCachedThreadPool();
    TransactionTemplate tx;
    User leader, member;
    Project project;

    record Ready(UUID deliverable, UUID requirement, UUID review, String fileName) {}
    record Reply(int status, JsonNode body) {}

    @BeforeEach void setup() {
        tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            leader = user(em, "동시팀장"); member = user(em, "동시팀원");
            project = project(em, leader);
            member(em, project, leader, "LEADER"); member(em, project, member, "MEMBER");
        });
    }

    @AfterEach void cleanup() {
        threads.shutdownNow();
        // 서버가 뜬 직후에는 점수 재계산 일정이 이 프로젝트의 점수를 만들고 있을 수 있다.
        // 지우는 사이에 새 줄이 끼어들어 프로젝트 삭제가 막히면 다시 지운다
        for (int attempt = 1; ; attempt++) {
            try {
                tx.executeWithoutResult(status -> deleteCreatedRows());
                return;
            } catch (DataIntegrityViolationException rowsAppeared) {
                if (attempt == 5) throw rowsAppeared;
                pause(300);
            }
        }
    }

    private void deleteCreatedRows() {
        UUID id = project.getId();
        jdbc.update("delete from deliverable_submissions where deliverable_id in (select id from deliverables where project_id = ?)", id);
        // 점수, 경보, 활동 기록은 프로젝트를 지워도 따라 지워지지 않는다
        for (String table : List.of("deliverable_confirmations", "review_rounds", "activity_logs", "contribution_scores", "alerts"))
            jdbc.update("delete from " + table + " where project_id = ?", id);
        // 파일 금고는 수정과 삭제를 트리거로 막는다. 테스트가 만든 줄을 지우는 동안만 끈다.
        // 테이블 변경도 이 트랜잭션에 묶여, 중간에 실패하면 트리거가 꺼진 채로 남지 않는다
        jdbc.execute("alter table file_vault disable trigger vault_immutable");
        jdbc.update("delete from file_vault where project_id = ?", id);
        jdbc.execute("alter table file_vault enable trigger vault_immutable");
        jdbc.update("delete from projects where id = ?", id);
        jdbc.update("delete from users where id in (?, ?)", leader.getId(), member.getId());
    }

    /**
     * 확정, 제출 기록, 제출물·요구사항·충족 확인·회차의 쓰기는 모두 무엇을 쓰기 전에 같은 제출물 줄부터 잠근다(K-20 6장).
     * 테스트가 그 줄을 잠근 채 요청을 보내고, 요청들이 DB에서 무엇을 기다리는지 본다.
     * 잠금을 건너뛴 명령은 끝나 버리거나, 제출물 줄이 아닌 다른 문장에서 멈춰 있어서 드러난다.
     */
    @Test void everyWriteLocksTheDeliverableRowFirst() throws Exception {
        Ready ready = ready("잠금");
        Object[] ids = {project.getId(), ready.deliverable()};
        Map<String, Callable<Reply>> writes = new LinkedHashMap<>();
        writes.put("확정", () -> confirm(ready));
        writes.put("제출 기록", () -> call(json(put(BASE + "/submission", ids), "{\"submittedAt\":\"2026-10-01T09:30:00+09:00\"}"), member));
        writes.put("제출물 수정", () -> call(json(put(BASE, ids), "{\"title\":\"바뀐 제목\",\"dueDate\":\"2026-10-20\"}"), member));
        writes.put("요구사항 추가", () -> call(json(post(BASE + "/requirements", ids), "{\"content\":\"새 요구사항\",\"required\":false}"), member));
        writes.put("충족 확인 해제", () -> call(delete(BASE + "/requirements/" + ready.requirement() + "/assessment", ids), member));
        writes.put("회차 열기", () -> call(json(post(BASE + "/reviews", ids), "{}"), member));

        Map<String, Future<Reply>> running = new LinkedHashMap<>();
        tx.executeWithoutResult(status -> {
            jdbc.queryForList("select id from deliverables where id = ? for update", ready.deliverable());
            writes.forEach((name, write) -> running.put(name, threads.submit(write)));
            List<String> waiting = statementsWaitingForALock(running);
            assertEquals(writes.size(), waiting.size(), "모든 쓰기가 잠금을 기다려야 한다: " + waiting);
            for (String statement : waiting)
                assertTrue(statement.startsWith("select") && statement.contains(" from deliverables "),
                        "제출물 줄을 잠그기 전에 다른 것을 쓰려 했다: " + statement);
        });
        for (Future<Reply> reply : running.values()) reply.get(30, TimeUnit.SECONDS);   // 잠금이 풀리면 모두 끝난다
    }

    /** 요청들이 모두 DB 잠금에서 멈출 때까지 기다렸다가, 멈춰 있는 문장들을 돌려준다. 먼저 끝난 요청이 있으면 바로 실패한다. */
    private List<String> statementsWaitingForALock(Map<String, Future<Reply>> running) {
        List<String> waiting = List.of();
        for (long deadline = System.currentTimeMillis() + 15_000; System.currentTimeMillis() < deadline; pause(100)) {
            running.forEach((name, reply) -> assertFalse(reply.isDone(), name + ": 제출물 잠금을 기다리지 않고 끝났다"));
            // 세션 목록은 트랜잭션 안에서 처음 읽은 것이 그대로 남는다. 읽을 때마다 비워야 지금 것을 본다
            jdbc.execute("select pg_stat_clear_snapshot()");
            waiting = jdbc.queryForList("select lower(query) from pg_stat_activity where datname = current_database()"
                    + " and wait_event_type = 'Lock' and pid <> pg_backend_pid()", String.class);
            if (waiting.size() == running.size()) break;
        }
        return waiting;
    }

    @Test void simultaneousConfirmationsStoreOne() throws Exception {
        Ready ready = ready("한꺼번에");
        List<Callable<Reply>> requests = Collections.nCopies(8, () -> confirm(ready));

        List<Reply> replies = race(requests);
        Set<String> confirmedAt = new HashSet<>();
        for (Reply reply : replies) {
            assertEquals(200, reply.status(), String.valueOf(reply.body()));
            confirmedAt.add(reply.body().at("/confirmation/confirmedAt").asText());
        }
        assertEquals(1, confirmedAt.size(), "모두 같은 확정 기록을 받는다: " + confirmedAt);
        assertEquals(1, count("deliverable_confirmations where deliverable_id = ?", ready.deliverable()));
    }

    /** 확정이 먼저면 해제가 409, 해제가 먼저면 확정이 409다. 확정됐는데 필수 요구사항이 미확인인 제출물은 생기지 않는다. */
    @Test void confirmationAndClearingAnAssessmentNeverBothSucceed() throws Exception {
        for (int i = 0; i < ROUNDS; i++) {
            Ready ready = ready("해제 " + i);
            String assessment = BASE + "/requirements/" + ready.requirement() + "/assessment";
            List<Reply> replies = race(List.of(() -> confirm(ready),
                    () -> call(delete(assessment, project.getId(), ready.deliverable()), member)));
            int confirmed = replies.get(0).status(), cleared = replies.get(1).status();
            assertTrue(confirmed == 200 && cleared == 409 || confirmed == 409 && cleared == 200,
                    "한쪽만 성공해야 한다: 확정 " + replies.get(0) + ", 해제 " + replies.get(1));
            assertEquals(confirmed == 200 ? 1 : 0, count("deliverable_confirmations where deliverable_id = ?", ready.deliverable()));
            assertEquals(confirmed == 200 ? 1 : 0, count("deliverable_requirements where id = ? and assessed_at is not null", ready.requirement()));
        }
    }

    /**
     * 확정과 내용이 다른 새 버전이 겹치면 둘 다 성공하거나 확정이 409다. 성공했다면 확정본은 승인된 회차의 버전이고,
     * 새 버전이 반영된 뒤에는 fileStillLatest가 false다(K-20 6장). 업로드는 파일 금고에 줄을 넣는 것으로 대신한다.
     */
    @Test void confirmationRacingANewerVersionKeepsTheApprovedVersion() throws Exception {
        for (int i = 0; i < ROUNDS; i++) {
            Ready ready = ready("새 버전 " + i);
            List<Reply> replies = race(List.of(() -> confirm(ready), () -> {
                tx.executeWithoutResult(status -> file(em, em.getReference(Project.class, project.getId()),
                        em.getReference(User.class, member.getId()), ready.fileName(), 2, 'c'));
                return new Reply(200, null);
            }));
            Reply confirmed = replies.get(0);
            JsonNode now = call(get(BASE + "/confirmation", project.getId(), ready.deliverable()), member).body();
            if (confirmed.status() == 200) {
                assertEquals(ready.review().toString(), now.at("/confirmation/reviewId").asText());
                assertEquals(1, now.at("/confirmation/file/version").asInt(), "확정본은 승인된 회차의 버전이다");
                assertFalse(now.at("/confirmation/fileStillLatest").asBoolean());
            } else {
                assertEquals(409, confirmed.status(), String.valueOf(confirmed.body()));
                assertTrue(confirmed.body().get("detail").asText().contains("최신 버전과 내용이 다릅니다"), confirmed.body().toString());
                assertTrue(now.get("confirmation").isNull());
            }
        }
    }

    /** 조건을 모두 채운 제출물을 커밋해 둔다. 파일 이름은 제출물마다 달라 서로의 최신 버전에 영향을 주지 않는다. */
    private Ready ready(String title) {
        return tx.execute(status -> {
            Project p = em.getReference(Project.class, project.getId());
            User lead = em.getReference(User.class, leader.getId()), uploader = em.getReference(User.class, member.getId());
            Deliverable d = deliverable(em, p, title);
            String fileName = title + "-" + UUID.randomUUID() + ".pdf";
            DeliverableRequirement requirement = requirement(em, d, true, lead);
            ReviewRound approved = round(em, d, 1, file(em, p, uploader, fileName, 1, 'a'), lead, "APPROVED");
            return new Ready(d.getId(), requirement.getId(), approved.getId(), fileName);
        });
    }

    private Reply confirm(Ready ready) throws Exception {
        return call(json(put(BASE + "/confirmation", project.getId(), ready.deliverable()), "{\"reviewId\":\"" + ready.review() + "\"}"), leader);
    }

    private Reply call(MockHttpServletRequestBuilder request, User as) throws Exception {
        var response = mvc.perform(request.header("Authorization", "Bearer " + jwt.generateAccessToken(as.getEmail()))).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        String body = response.getContentAsString();
        return new Reply(response.getStatus(), body.isEmpty() ? json.createObjectNode() : json.readTree(body));
    }

    /** 요청들을 한 번에 출발시켜 겹치게 한다. 결과는 넘긴 순서대로다. */
    private List<Reply> race(List<Callable<Reply>> requests) throws Exception {
        CyclicBarrier start = new CyclicBarrier(requests.size());
        List<Future<Reply>> running = new ArrayList<>();
        for (Callable<Reply> request : requests) running.add(threads.submit(() -> { start.await(10, TimeUnit.SECONDS); return request.call(); }));
        List<Reply> replies = new ArrayList<>();
        for (Future<Reply> future : running) replies.add(future.get(30, TimeUnit.SECONDS));
        return replies;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private int count(String fromWhere, UUID id) {
        return jdbc.queryForObject("select count(*) from " + fromWhere, Integer.class, id);
    }
}
