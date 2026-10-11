package com.blackbox.service;

import com.blackbox.dto.UpdateTaskRequest;
import com.blackbox.entity.*;
import com.blackbox.scheduler.ScoreScheduler;
import com.blackbox.security.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.http.MediaType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.*;
import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 별도 트랜잭션 사이의 잠금을 실제 PostgreSQL에서 확인한다. HTTP 권한 검사는 별도 통합 테스트에 둔다. */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0") @ActiveProfiles("test") @AutoConfigureMockMvc
class TaskWriteConcurrencyTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired TaskService service;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean ScoreScheduler scheduler;
    TransactionTemplate tx;
    User actor, uploader;
    Project project;
    Deliverable first, second;
    Task task, other;
    ExecutorService pool;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager); pool = Executors.newFixedThreadPool(2);
        tx.executeWithoutResult(status -> {
            actor = user(em, "B22동시검사"); project = project(em, actor); member(em, project, actor, "LEADER");
            uploader = user(em, "B22작성자"); member(em, project, uploader, "MEMBER");
            first = deliverable(em, project, "첫 제출물"); second = deliverable(em, project, "둘째 제출물");
            task = task(first); other = task(second);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void confirmationAndReopenRespectWhicheverObtainsTheLockFirst(boolean confirmFirst) throws Exception {
        String[] paths = tx.execute(status -> {
            var old = round(em, first, 1, uploader, "CHANGES_REQUESTED");
            var approved = round(em, first, 2, file(em, project, uploader, "경쟁.pdf", 1, 'a'), actor, "APPROVED");
            var comment = new ReviewComment(); comment.setProject(project); comment.setDeliverable(first);
            comment.setReview(old); comment.setAuthor(actor); comment.setContent("반영 확인한 의견");
            comment.setResolvedBy(actor); comment.setResolvedAt(java.time.OffsetDateTime.now()); em.persist(comment); em.flush();
            String base = "/api/projects/" + project.getId() + "/deliverables/" + first.getId();
            return new String[]{base + "/confirmation", "{\"reviewId\":\"" + approved.getId() + "\"}",
                    base + "/reviews/" + old.getId() + "/comments/" + comment.getId() + "/resolution"};
        });
        List<Future<Integer>> waiting = new ArrayList<>();
        tx.executeWithoutResult(status -> {
            jdbc.queryForList("select id from deliverables where id=? for update", first.getId());
            waiting.add(pool.submit(() -> confirmFirst ? call(delete(paths[2])) : call(put(paths[0]).content(paths[1]))));
            awaitDeliverableWait(waiting.get(0));
            assertEquals(200, confirmFirst ? call(put(paths[0]).content(paths[1])) : call(delete(paths[2])));
        });
        assertEquals(409, waiting.get(0).get(20, TimeUnit.SECONDS));
        assertEquals(confirmFirst ? 1 : 0, jdbc.queryForObject("select count(*) from deliverable_confirmations where project_id=?", Integer.class, project.getId()));
        assertEquals(confirmFirst ? 0 : 1, jdbc.queryForObject("select count(*) from review_comments where project_id=? and resolved_at is null", Integer.class, project.getId()));
    }

    private int call(MockHttpServletRequestBuilder request) {
        try {
            return mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + jwt.generateAccessToken(actor.getEmail())))
                    .andReturn().getResponse().getStatus();
        } catch (Exception error) { throw new AssertionError(error); }
    }
    @AfterEach void cleanup() throws Exception {
        pool.shutdownNow(); assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        if (project == null) return;
        tx.executeWithoutResult(status -> {
            jdbc.update("delete from deliverable_confirmations where project_id=?", project.getId());
            jdbc.update("delete from review_comments where project_id=?", project.getId());
            jdbc.update("delete from review_rounds where project_id=?", project.getId());
            jdbc.update("delete from activity_logs where project_id=?", project.getId());
            // 커밋된 검사 파일만 정리한다. 트리거 변경도 같은 트랜잭션이어서 실패 시 함께 되돌아간다.
            jdbc.execute("alter table file_vault disable trigger vault_immutable");
            jdbc.update("delete from file_vault where project_id=?", project.getId());
            jdbc.execute("alter table file_vault enable trigger vault_immutable");
            for (String table : List.of("tasks", "deliverables", "project_members"))
                jdbc.update("delete from " + table + " where project_id=?", project.getId());
            jdbc.update("delete from projects where id=?", project.getId());
            jdbc.update("delete from users where id in (?,?)", actor.getId(), uploader.getId());
        });
    }

    @Test void taskWriteWaitsForDeliverableLockAndRejectsChangedAssociation() throws Exception {
        List<Future<?>> requests = new ArrayList<>();
        tx.executeWithoutResult(status -> {
            jdbc.queryForList("select id from deliverables where id=? for update", first.getId());
            requests.add(pool.submit(() -> service.updateTask(project.getId(), task.getId(), update(null), actor)));
            awaitDeliverableWait(requests.get(0));
            // 앞선 이동이 커밋되는 상황을 재현한다. 대기 중인 요청은 오래된 연결을 믿고 수정하면 안 된다.
            jdbc.update("update tasks set deliverable_id=? where id=?", second.getId(), task.getId());
        });
        ExecutionException failure = assertThrows(ExecutionException.class, () -> requests.get(0).get(20, TimeUnit.SECONDS));
        assertInstanceOf(ResponseStatusException.class, failure.getCause());
        assertEquals(409, ((ResponseStatusException) failure.getCause()).getStatusCode().value());
        assertEquals("원래 제목", jdbc.queryForObject("select title from tasks where id=?", String.class, task.getId()));
        assertEquals(second.getId(), jdbc.queryForObject("select deliverable_id from tasks where id=?", UUID.class, task.getId()));
    }

    @Test void oppositeMovesFinishWithoutDeadlock() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Future<?> one = pool.submit(() -> { start.await(); return service.updateTask(project.getId(), task.getId(), update(second.getId()), actor); });
        Future<?> two = pool.submit(() -> { start.await(); return service.updateTask(project.getId(), other.getId(), update(first.getId()), actor); });
        start.countDown(); one.get(20, TimeUnit.SECONDS); two.get(20, TimeUnit.SECONDS);
        assertEquals(second.getId(), jdbc.queryForObject("select deliverable_id from tasks where id=?", UUID.class, task.getId()));
        assertEquals(first.getId(), jdbc.queryForObject("select deliverable_id from tasks where id=?", UUID.class, other.getId()));
    }

    private void awaitDeliverableWait(Future<?> request) {
        for (long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10); System.nanoTime() < until;) {
            assertFalse(request.isDone(), "제출물 잠금을 기다리지 않고 쓰기가 끝났다");
            jdbc.execute("select pg_stat_clear_snapshot()");
            var waiting = jdbc.queryForList("select lower(query) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and pid<>pg_backend_pid()", String.class);
            if (waiting.stream().anyMatch(sql -> sql.startsWith("select") && sql.contains(" from deliverables "))) return;
            try { Thread.sleep(50); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        }
        fail("제출물 잠금 대기를 확인하지 못했다");
    }
    private Task task(Deliverable delivery) {
        Task value = new Task(); value.setProject(project); value.setCreatedBy(actor); value.setDeliverable(delivery);
        value.setTitle("원래 제목"); value.setStatus("TODO"); value.setPriority("MEDIUM"); em.persist(value); return value;
    }
    private UpdateTaskRequest update(UUID delivery) {
        return new UpdateTaskRequest("바뀐 제목", null, null, null, null, delivery, null, false, false, null);
    }
}
