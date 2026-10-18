package com.blackbox.service;

import com.blackbox.dto.*;
import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.repository.UserNotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C-56: 업무 배정 알림, 가벼운 업무 생성 흐름, 전원 담당자 막힘, 동시 요청을 실제 PostgreSQL에서 검사한다.
 * 재배정·동시성 검사는 실제 요청처럼 트랜잭션을 나눠야 해서 테스트 트랜잭션 없이 실행하고 커밋된 데이터를 남긴다(전용 테스트 DB).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({NotificationService.class, ProjectAccessChecker.class, TaskService.class, TaskWorkflowService.class,
        JacksonAutoConfiguration.class})
class NotificationAssignmentDatabaseTest {
    @Autowired EntityManager em;
    @Autowired TaskService tasks;
    @Autowired TaskWorkflowService workflow;
    @Autowired NotificationService notifications;
    @Autowired UserNotificationRepository repo;
    @Autowired PlatformTransactionManager txManager;
    @MockBean ActivityLogService activity;
    @MockBean NotionService notion;
    @MockBean AlertService alerts;
    @MockBean DiscordNotificationService discord;
    @MockBean ScoreService scores;
    @MockBean DeliverableService deliverables;

    class Team {
        final User leader = user("민지"), worker = user("현우"), peer = user("유나"), peer2 = user("지호"), observer = user("관찰자");
        final Project project = new Project();
        Team() {
            project.setName("엑스포 팀"); project.setCreatedBy(leader); em.persist(project);
            member(leader, "LEADER"); member(worker, "MEMBER"); member(peer, "MEMBER"); member(peer2, "MEMBER");
            member(observer, "OBSERVER"); em.flush();
        }
        UUID pid() { return project.getId(); }
        void member(User u, String role) {
            ProjectMember m = new ProjectMember(); m.setProject(project); m.setUser(u); m.setRole(role); em.persist(m);
        }
    }

    // ── 1. 배정 알림 ──────────────────────────────────────────────────────

    @Test void createNotifiesEachNewAssigneeExceptActor() {
        Team t = new Team();
        // 팀장이 자신을 포함해 지정하면 본인은 제외. 같은 id를 두 번 보내도 한 건.
        var task = tasks.createTask(t.pid(), create("설문 문항 작성", List.of(t.worker.getId(), t.peer.getId(), t.leader.getId(), t.peer.getId())), t.leader);
        assertEquals(1, count(t, t.worker)); assertEquals(1, count(t, t.peer));
        assertEquals(0, count(t, t.leader)); assertEquals(0, count(t, t.peer2)); assertEquals(0, count(t, t.observer));
        var item = notifications.list(t.pid(), null, null, false, t.worker).items().get(0);
        assertEquals("TASK_ASSIGNED", item.type()); assertEquals(task.id(), item.taskId());
        assertEquals("민지", item.actorName()); assertNull(item.fileId()); assertNull(item.messageId()); assertTrue(item.targetAvailable());
        // 관찰자는 담당자로 지정할 수 없다(기존 규칙). 실패한 생성은 알림이 없다.
        assertThrows(ResponseStatusException.class,
                () -> tasks.createTask(t.pid(), create("x", List.of(t.peer2.getId(), t.observer.getId())), t.leader));
        assertEquals(0, count(t, t.peer2));
    }

    @Test void changeNotifiesOnlyNewlyAddedAndEditsDoNotRenotify() {
        Team t = new Team();
        var task = tasks.createTask(t.pid(), create("비교 조사", List.of(t.worker.getId())), t.leader);
        tasks.setAssignees(t.pid(), task.id(), new AssignTaskRequest(List.of(t.worker.getId())), t.leader);
        assertEquals(1, count(t, t.worker), "같은 목록 재저장은 재알림 없음");
        tasks.setAssignees(t.pid(), task.id(), new AssignTaskRequest(List.of(t.peer.getId(), t.worker.getId())), t.leader);
        assertEquals(1, count(t, t.worker)); assertEquals(1, count(t, t.peer), "새로 추가된 사람만");
        tasks.updateTask(t.pid(), task.id(), new UpdateTaskRequest(null, "설명 보강", null, null,
                LocalDate.of(2026, 10, 20), null, null, null, null, "출처 포함 비교표"), t.worker);
        assertEquals(1, count(t, t.worker)); assertEquals(1, count(t, t.peer), "설명·기한·기준 수정은 재알림 없음");
        // 스스로 담당자로 추가하면 알림 없음
        tasks.setAssignees(t.pid(), task.id(), new AssignTaskRequest(List.of(t.peer.getId(), t.worker.getId(), t.peer2.getId())), t.peer2);
        assertEquals(0, count(t, t.peer2));
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unassignThenReassignIsANewEvent() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Team t = tx.execute(s -> new Team());
        UUID taskId = tx.execute(s -> tasks.createTask(t.pid(), create("현장 조사", List.of(t.worker.getId())), t.leader).id());
        tx.executeWithoutResult(s -> tasks.setAssignees(t.pid(), taskId, new AssignTaskRequest(List.of(t.peer.getId())), t.leader));
        tx.executeWithoutResult(s -> tasks.setAssignees(t.pid(), taskId, new AssignTaskRequest(List.of(t.peer.getId(), t.worker.getId())), t.leader));
        assertEquals(2L, (long) tx.<Long>execute(s -> count(t, t.worker)), "해제 후 재배정은 새 알림");
        assertEquals(1L, (long) tx.<Long>execute(s -> count(t, t.peer)));
    }

    // ── 2. 가벼운 생성 흐름과 기존 완료 조건 유지 ─────────────────────────

    @Test void titleOnlyTaskStillNeedsCriteriaAndIndependentReviewer() {
        Team t = new Team();
        var task = tasks.createTask(t.pid(), create("발표 자료 초안", null), t.worker);
        assertNull(task.dueDate()); assertTrue(task.assignees().isEmpty()); assertNull(task.completionCriteria());
        assertEquals(0, repo.findAll().stream().filter(n -> n.getProjectId().equals(t.pid())).count(), "담당자 없으면 배정 알림 없음");
        tasks.updateStatus(t.pid(), task.id(), new UpdateTaskStatusRequest("IN_PROGRESS"), t.worker);
        // 담당자가 없으면 생성자가 수행자. 완료 기준이 없으면 완료 확인 요청은 409(기존 규칙 유지).
        var noCriteria = assertThrows(ResponseStatusException.class, () -> workflow.request(t.pid(), task.id(), t.worker));
        assertEquals(409, noCriteria.getStatusCode().value());
        assertTrue(noCriteria.getReason().contains("완료 조건"));
        tasks.updateTask(t.pid(), task.id(), new UpdateTaskRequest(null, null, null, null, null, null, null, null, null, "10장 이내"), t.worker);
        workflow.request(t.pid(), task.id(), t.worker);
        assertEquals(0, count(t, t.worker, "TASK_COMPLETION_REQUESTED"));
        assertEquals(1, count(t, t.leader, "TASK_COMPLETION_REQUESTED"));
        // 본인 승인 불가 유지
        assertThrows(ForbiddenException.class, () -> workflow.decide(t.pid(), task.id(), true, true, null, t.worker));
        workflow.decide(t.pid(), task.id(), true, true, null, t.peer);
    }

    @Test void allAssigneesCannotRequestAndLegacyPendingCanRecover() {
        Team t = new Team();
        var all = List.of(t.leader.getId(), t.worker.getId(), t.peer.getId(), t.peer2.getId());
        var task = tasks.createTask(t.pid(), new CreateTaskRequest("최종 보고서", null, null, null, null, all,
                "IN_PROGRESS", null, null, "목차 확정"), t.leader);
        assertFalse(workflow.view(t.pid(),task.id(),t.worker).canRequest());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->workflow.request(t.pid(),task.id(),t.worker)).getStatusCode().value());
        assertEquals(0, repo.findAll().stream().filter(n -> n.getProjectId().equals(t.pid())
                && n.getType().equals("TASK_COMPLETION_REQUESTED")).count(), "확인할 사람이 없어 알림 0건");
        for (User u : List.of(t.leader, t.worker, t.peer, t.peer2)) {
            assertFalse(workflow.view(t.pid(), task.id(), u).canApprove());
            assertThrows(ForbiddenException.class, () -> workflow.decide(t.pid(), task.id(), false, false, "보완", u));
        }
        // 이전 서버에서 저장된 대기 상태를 재현한다.
        Task legacy=em.find(Task.class,task.id());legacy.setCompletionRequestedAt(java.time.OffsetDateTime.now().minusDays(1));
        legacy.setCompletionRequestedBy(t.worker);em.flush();
        assertTrue(workflow.view(t.pid(),task.id(),t.worker).canCancel());
        workflow.cancel(t.pid(),task.id(),"독립 확인자 확보",t.worker);
        tasks.setAssignees(t.pid(),task.id(),new AssignTaskRequest(List.of(t.worker.getId())),t.leader);
        workflow.request(t.pid(),task.id(),t.worker);
        assertEquals("DONE",workflow.decide(t.pid(),task.id(),true,true,null,t.peer).status());
    }

    // ── 3. 동시 요청 ──────────────────────────────────────────────────────

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDuplicateRequestsCreateOneNotificationPerRecipient() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Team t = tx.execute(s -> new Team());
        UUID taskId = tx.execute(s -> tasks.createTask(t.pid(), new CreateTaskRequest("통합본 정리", null, null, null, null,
                List.of(t.worker.getId()), "IN_PROGRESS", null, null, "오탈자 없음"), t.leader).id());
        // 같은 담당자가 완료 요청을 동시에 두 번 보낸다(더블 클릭·재시도).
        race(() -> workflow.request(t.pid(), taskId, t.worker), () -> workflow.request(t.pid(), taskId, t.worker));
        for (User u : List.of(t.leader, t.peer, t.peer2))
            assertEquals(1L, (long) tx.<Long>execute(s -> count(t, u, "TASK_COMPLETION_REQUESTED")), u.getName());

        // 같은 이벤트 키 INSERT 자체를 동시에 실행해도 고유 제약에서 한 건만 남는다.
        String key = "RACE:" + UUID.randomUUID();
        Runnable insert = () -> tx.executeWithoutResult(s -> repo.insertIgnoringDuplicate(t.pid(), t.peer.getId(), t.worker.getId(),
                "현우", "TASK_FILE_UPLOADED", key, taskId, "통합본 정리", null, null, null, null,
                java.time.OffsetDateTime.now()));
        race(insert, insert);
        assertEquals(1L, (long) tx.<Long>execute(s -> repo.findAll().stream().filter(n -> n.getEventKey().equals(key)).count()));
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentReadKeepsSingleFirstReadTime() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Team t = tx.execute(s -> new Team());
        tx.executeWithoutResult(s -> tasks.createTask(t.pid(), create("현장 사진 정리", List.of(t.worker.getId())), t.leader));
        UUID id = tx.execute(s -> notifications.list(t.pid(), null, null, false, t.worker).items().get(0).id());
        List<NotificationDtos.ReadResult> results = Collections.synchronizedList(new ArrayList<>());
        race(() -> results.add(notifications.markRead(t.pid(), id, t.worker)),
             () -> results.add(notifications.markRead(t.pid(), id, t.worker)));
        assertEquals(2, results.size());
        assertEquals(results.get(0).readAt(), results.get(1).readAt(), "두 응답이 같은 readAt");
        var stored = tx.execute(s -> repo.findById(id).orElseThrow().getReadAt());
        assertEquals(stored.toInstant(), results.get(0).readAt().toInstant(), "DB 값과 일치");
        assertEquals(0L, (long) tx.<Long>execute(s -> notifications.unread(t.pid(), t.worker).unreadCount()));
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cancelAndApproveSerializeWithoutContradictoryOutcome() throws Exception {
        TransactionTemplate tx=new TransactionTemplate(txManager);
        Team t=tx.execute(s->new Team());
        UUID id=tx.execute(s->tasks.createTask(t.pid(),new CreateTaskRequest("경합 검사",null,null,null,null,
                List.of(t.worker.getId()),"IN_PROGRESS",null,null,"결과 확인"),t.leader).id());
        workflow.request(t.pid(),id,t.worker);
        var successes=new java.util.concurrent.atomic.AtomicInteger();
        var conflicts=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<Runnable> attempt=action->{try {action.run();successes.incrementAndGet();}
            catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());conflicts.incrementAndGet();}};
        race(()->attempt.accept(()->workflow.cancel(t.pid(),id,"담당 조정",t.leader)),
                ()->attempt.accept(()->workflow.decide(t.pid(),id,true,true,null,t.peer)));
        assertEquals(1,successes.get());assertEquals(1,conflicts.get());
        tx.executeWithoutResult(s->{Task stored=em.find(Task.class,id);assertNull(stored.getCompletionRequestedAt());
            assertEquals("DONE".equals(stored.getStatus()),stored.getCompletionApprovedAt()!=null);});
    }

    // ── fixture ───────────────────────────────────────────────────────────

    /** 두 작업을 최대한 같은 순간에 시작하고 둘 다 끝날 때까지 기다린다. 예외는 그대로 다시 던진다. */
    private void race(Runnable a, Runnable b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable r : List.of(a, b)) futures.add(pool.submit(() -> { ready.countDown(); go.await(); r.run(); return null; }));
            ready.await(); go.countDown();
            for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    private CreateTaskRequest create(String title, List<UUID> assignees) {
        return new CreateTaskRequest(title, null, null, null, null, assignees, null, null, null, null);
    }

    private long count(Team t, User u) { return count(t, u, "TASK_ASSIGNED"); }
    private long count(Team t, User u, String type) {
        em.flush();
        return repo.findAll().stream().filter(n -> n.getProjectId().equals(t.pid()) && n.getRecipientId().equals(u.getId())
                && type.equals(n.getType())).count();
    }

    private User user(String name) {
        User u = new User(); u.setName(name); u.setEmail(UUID.randomUUID() + "@c56.example.invalid");
        u.setPasswordHash("test-only-not-used-for-authentication"); u.setRole("STUDENT"); em.persist(u); return u;
    }
}
