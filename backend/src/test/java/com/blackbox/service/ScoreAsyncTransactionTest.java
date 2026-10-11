package com.blackbox.service;

import com.blackbox.dto.UpdateTaskStatusRequest;
import com.blackbox.entity.*;
import com.blackbox.repository.*;
import com.blackbox.scheduler.ScoreScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
class ScoreAsyncTransactionTest {
    @SpyBean ScoreService scores;
    @Autowired TaskService tasks;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ProjectMemberRepository members;
    @Autowired TaskRepository taskRepository;
    @Autowired TaskAssigneeRepository assignees;
    @Autowired ContributionScoreRepository scoreRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @SpyBean AlertService alerts;
    // 시작 직후의 전체 재계산이 비동기 업무 이벤트 대신 관찰 조건을 채우면 회귀를 놓친다.
    @MockBean ScoreScheduler scheduler;
    Fixture fixture;

    record Fixture(User user, Project project, Task task) {}
    record WorkerResult(boolean transactionActive, long threadId, int completion) {}

    Fixture seed() {
        // 비동기 스레드가 실제로 읽을 수 있도록 준비 데이터는 요청과 별도 트랜잭션에서 커밋한다.
        fixture = new TransactionTemplate(transactionManager).execute(status -> {
            User user = new User();
            user.setEmail("score-test-" + UUID.randomUUID() + "@example.invalid");
            user.setName("검사 계정");
            user.setPasswordHash("test-only-not-a-password");
            user.setRole("STUDENT");
            users.save(user);
            Project project = new Project();
            project.setName("비동기 기여도 검사");
            project.setCreatedBy(user);
            projects.save(project);
            ProjectMember member = new ProjectMember();
            member.setProject(project);
            member.setUser(user);
            member.setRole("LEADER");
            members.save(member);
            Task task = new Task();
            task.setProject(project);
            task.setCreatedBy(user);
            task.setTitle("검사용 업무");
            task.setStatus("TODO");
            task.setPriority("MEDIUM");
            taskRepository.save(task);
            TaskAssignee assignee = new TaskAssignee();
            assignee.setTask(task);
            assignee.setUser(user);
            assignees.save(assignee);
            return new Fixture(user, project, task);
        });
        return fixture;
    }

    @AfterEach void removeOnlyThisTestsFixtures() {
        if (fixture == null) return;
        // 비동기 검사는 준비 자료를 커밋하므로 테스트 롤백으로 지워지지 않는다.
        // 이번 테스트가 생성한 UUID만 사용하며 기존 검사 자료나 운영 자료는 선택하지 않는다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID projectId = fixture.project().getId();
            jdbc.update("DELETE FROM alerts WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM activity_logs WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM contribution_scores WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM task_assignees WHERE task_id = ?", fixture.task().getId());
            jdbc.update("DELETE FROM tasks WHERE id = ? AND project_id = ?", fixture.task().getId(), projectId);
            jdbc.update("DELETE FROM project_members WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM projects WHERE id = ?", projectId);
            jdbc.update("DELETE FROM users WHERE id = ?", fixture.user().getId());
        });
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM projects WHERE id = ?", Integer.class, fixture.project().getId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, fixture.user().getId()));
    }

    CompletableFuture<WorkerResult> observe(Fixture fixture, boolean fail) {
        CompletableFuture<WorkerResult> result = new CompletableFuture<>();
        // 경보 서비스에도 @Transactional이 있으므로 그 안에서만 관찰하면 계산 전체의 누락을 놓친다.
        // 비동기 진입점이 내부 재계산을 부르는 시점부터 트랜잭션이 있는지 확인한다.
        doAnswer(invocation -> {
            UUID projectId = invocation.getArgument(0);
            if (!projectId.equals(fixture.project().getId())) return invocation.callRealMethod();
            boolean active = TransactionSynchronizationManager.isActualTransactionActive();
            long thread = Thread.currentThread().getId();
            if (active) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int completion) {
                        result.complete(new WorkerResult(true, thread, completion));
                    }
                });
            }
            try {
                return invocation.callRealMethod();
            } finally {
                if (!active) result.complete(new WorkerResult(false, thread, -1));
            }
        }).when(scores).recalculate(any(UUID.class));
        doAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            if (!project.getId().equals(fixture.project().getId())) return invocation.callRealMethod();
            // 점수 INSERT가 실제 DB에 전달된 뒤 실패하도록 하여 부분 저장도 검출한다.
            scoreRepository.flush();
            if (fail) throw new IllegalStateException("검사용 경보 실패");
            return invocation.callRealMethod();
        }).when(alerts).checkAlerts(any(Project.class), anyList(), anyList());
        return result;
    }

    @Test void taskCompletionCommitsScoreOnAsyncWorker() throws Exception {
        Fixture fixture = seed();
        CompletableFuture<WorkerResult> completion = observe(fixture, false);
        long requestThread = Thread.currentThread().getId();

        tasks.updateStatus(fixture.project().getId(), fixture.task().getId(),
                new UpdateTaskStatusRequest("DONE"), fixture.user());

        WorkerResult worker = completion.get(10, TimeUnit.SECONDS);
        assertTrue(worker.transactionActive());
        assertNotEquals(requestThread, worker.threadId());
        assertEquals(TransactionSynchronization.STATUS_COMMITTED, worker.completion());
        ContributionScore saved = scoreRepository.findByProjectAndUser(fixture.project(), fixture.user()).orElseThrow();
        assertTrue(saved.isTaskParticipated());
        assertEquals("DONE", taskRepository.findById(fixture.task().getId()).orElseThrow().getStatus());
    }

    @Test void calculationFailureRollsBackScoreButKeepsCompletedTask() throws Exception {
        Fixture fixture = seed();
        CompletableFuture<WorkerResult> completion = observe(fixture, true);

        tasks.updateStatus(fixture.project().getId(), fixture.task().getId(),
                new UpdateTaskStatusRequest("DONE"), fixture.user());

        WorkerResult worker = completion.get(10, TimeUnit.SECONDS);
        assertTrue(worker.transactionActive());
        assertEquals(TransactionSynchronization.STATUS_ROLLED_BACK, worker.completion());
        assertTrue(scoreRepository.findByProjectAndUser(fixture.project(), fixture.user()).isEmpty());
        assertEquals("DONE", taskRepository.findById(fixture.task().getId()).orElseThrow().getStatus());
    }
}
