package com.blackbox.service;

import com.blackbox.dto.UpdateTaskStatusRequest;
import com.blackbox.entity.*;
import com.blackbox.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
    @Autowired ScoreService scores;
    @Autowired TaskService tasks;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ProjectMemberRepository members;
    @Autowired TaskRepository taskRepository;
    @Autowired TaskAssigneeRepository assignees;
    @Autowired ContributionScoreRepository scoreRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @SpyBean AlertService alerts;

    record Fixture(User user, Project project, Task task) {}
    record WorkerResult(boolean transactionActive, long threadId, int completion) {}

    Fixture seed() {
        // 비동기 스레드가 실제로 읽을 수 있도록 준비 데이터는 요청과 별도 트랜잭션에서 커밋한다.
        return new TransactionTemplate(transactionManager).execute(status -> {
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
    }

    CompletableFuture<WorkerResult> observe(Fixture fixture, boolean fail) {
        CompletableFuture<WorkerResult> result = new CompletableFuture<>();
        doAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            if (!project.getId().equals(fixture.project().getId())) return invocation.callRealMethod();
            boolean active = TransactionSynchronizationManager.isActualTransactionActive();
            long thread = Thread.currentThread().getId();
            if (!active) {
                result.complete(new WorkerResult(false, thread, -1));
                if (fail) throw new IllegalStateException("검사용 경보 실패");
                return invocation.callRealMethod();
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int completion) {
                    result.complete(new WorkerResult(true, thread, completion));
                }
            });
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
