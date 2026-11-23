package com.blackbox.repository;

import com.blackbox.entity.*;
import com.blackbox.scheduler.ScoreScheduler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static com.blackbox.repository.EmailDeliveryRepository.*;
import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 전용 실제 PostgreSQL. SMTP/worker를 등록하지 않고 커밋된 대기와 별도 연결의 경합을 확인한다. */
@SpringBootTest(properties = "spring.datasource.hikari.minimum-idle=0")
@ActiveProfiles("test")
class EmailDeliveryRepositoryDatabaseTest {
    @Autowired EmailDeliveryRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;
    @MockitoBean ScoreScheduler scoreScheduler;
    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    final List<UUID> syntheticProjects = new ArrayList<>();
    record Team(UUID projectId, UUID actorId, UUID recipientId, UUID taskId) {}

    @AfterEach void finishOnlyThisTestsSyntheticRows() {
        for (UUID id : syntheticProjects) jdbc.update("""
                UPDATE notification_email_outbox SET status='SKIPPED', sent_at=NULL
                WHERE project_id=? AND status IN ('PENDING','PROCESSING')
                """, id);
    }

    @Test void claimReturnsImmutableSnapshotWithDatabasePrecisionAndRedactedString() {
        Team t = team(); UUID id = row(t, now);
        var lease = repository.claim(1, now).getFirst();
        assertEquals(id, lease.id()); assertEquals(t.projectId(), lease.projectId());
        assertEquals(t.actorId(), lease.actorId()); assertEquals(t.recipientId(), lease.recipientId());
        assertEquals(t.taskId(), lease.taskId()); assertEquals("TASK_COMPLETION_REQUESTED", lease.eventType());
        assertEquals("합성 요청자", lease.actorName()); assertEquals("합성 결과", lease.targetTitle());
        assertNull(lease.reviewId()); assertNull(lease.deliverableId());
        assertEquals(now.toInstant(), lease.claimedAt().toInstant()); assertEquals(1, lease.attemptCount());
        assertFalse(lease.toString().contains("합성"));
        assertTrue(repository.claim(1, now).isEmpty());
    }

    @Test void futureRowsAreNotClaimedAndBatchCannotExceedTwentyFive() {
        Team t = team(); UUID future = row(t, now.plusSeconds(5));
        assertTrue(repository.claim(25, now).isEmpty());
        for (int i=0; i<28; i++) row(t, now);
        assertEquals(25, repository.claim(25, now).size());
        assertEquals(3, repository.claim(25, now).size());
        assertEquals("PENDING", status(future));
        rejected(() -> repository.claim(0, now));
        rejected(() -> repository.claim(26, now));
    }

    @Test void concurrentWorkersClaimDifferentRows() throws Exception {
        Team t = team(); Set<UUID> expected = Set.of(row(t, now), row(t, now));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            Callable<EmailDeliveryLease> work = () -> { ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS)); return repository.claim(1, now).getFirst(); };
            Future<EmailDeliveryLease> a = pool.submit(work), b = pool.submit(work);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            assertEquals(expected, Set.of(a.get(10, TimeUnit.SECONDS).id(), b.get(10, TimeUnit.SECONDS).id()));
        }
    }

    @Test void lockedAndUncommittedRowsAreSkippedWithoutWaiting() throws Exception {
        Team t = team(); UUID locked = row(t, now), free = row(t, now);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM notification_email_outbox WHERE id=? FOR UPDATE")) {
                lock.setObject(1, locked); lock.executeQuery().close();
            }
            // 같은 행 잠금에 대한 대기 없이 다른 대기만 획득해야 한다.
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
                var claimed = repository.claim(25, now);
                assertEquals(List.of(free), claimed.stream().map(EmailDeliveryLease::id).toList());
            });
            connection.rollback();
        }
        assertEquals(locked, repository.claim(1, now).getFirst().id());
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            UUID uncommitted = UUID.randomUUID();
            try (var insert = connection.prepareStatement(insertSql())) {
                bind(insert, uncommitted, t, now); insert.executeUpdate();
            }
            assertTrue(repository.claim(25, now).isEmpty());
            connection.rollback();
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notification_email_outbox WHERE id=?", Integer.class, uncommitted));
        }
    }

    @Test void leaseExpiryRejectsOldCompletionAndAllowsRecovery() {
        Team t = team(); row(t, now); var old = repository.claim(1, now).getFirst();
        OffsetDateTime expired = now.plus(LEASE);
        assertFalse(repository.complete(old, Completion.SENT, null, expired));
        var fresh = repository.claim(1, expired).getFirst();
        assertEquals(2, fresh.attemptCount()); assertNotEquals(old.claimedAt(), fresh.claimedAt());
        assertFalse(repository.complete(old, Completion.SENT, null, expired));
        assertTrue(repository.complete(fresh, Completion.SENT, null, expired.plusSeconds(1)));
        assertFalse(repository.complete(fresh, Completion.SENT, null, expired.plusSeconds(2)));
        assertEquals("SENT", status(fresh.id()));
        assertEquals(expired.plusSeconds(1).toInstant(), jdbc.queryForObject("SELECT sent_at FROM notification_email_outbox WHERE id=?", OffsetDateTime.class, fresh.id()).toInstant());
    }

    @Test void temporaryFailureRetriesWithDelayAndStopsAtThirdAttempt() {
        Team t = team(); UUID id = row(t, now); OffsetDateTime at = now;
        for (int attempt=1; attempt<=3; attempt++) {
            var lease = repository.claim(1, at).getFirst(); assertEquals(attempt, lease.attemptCount());
            assertTrue(repository.complete(lease, Completion.RETRY, Failure.TEMPORARY_FAILURE, at));
            if (attempt<3) {
                assertTrue(repository.claim(1, at.plusSeconds(30L*attempt-1)).isEmpty());
                at = at.plusSeconds(30L*attempt);
            }
        }
        assertEquals("FAILED", status(id)); assertTrue(repository.claim(25, at.plusDays(1)).isEmpty());
        assertEquals(3, jdbc.queryForObject("SELECT attempt_count FROM notification_email_outbox WHERE id=?", Integer.class, id));
    }

    @Test void thirdAttemptCrashEventuallyBecomesFailedWithoutFourthAttempt() {
        Team t = team(); UUID id = row(t, now);
        for(int attempt=0; attempt<3; attempt++) assertEquals(attempt+1, repository.claim(1, now.plus(LEASE.multipliedBy(attempt))).getFirst().attemptCount());
        assertTrue(repository.claim(1, now.plus(LEASE.multipliedBy(3))).isEmpty());
        assertEquals("FAILED", status(id));
        assertEquals("MAX_ATTEMPTS", jdbc.queryForObject("SELECT error_code FROM notification_email_outbox WHERE id=?", String.class, id));
    }

    @Test void permanentAuthenticationAndRemovedEligibilityEndWithoutRetry() {
        Team t = team();
        for (Failure reason : List.of(Failure.PERMANENT_FAILURE, Failure.AUTHENTICATION_FAILURE, Failure.CONDITION_CHANGED)) {
            UUID id = row(t, now); var lease = repository.claim(1, now).getFirst();
            Completion completion = reason == Failure.CONDITION_CHANGED ? Completion.SKIPPED : Completion.FAILED;
            assertTrue(repository.complete(lease, completion, reason, now));
            assertEquals(completion.name(), status(id));
        }
        UUID id = row(t, now); var lease = repository.claim(1, now).getFirst();
        rejected(() -> repository.complete(lease, Completion.SENT, Failure.UNKNOWN_OUTCOME, now));
        rejected(() -> repository.complete(lease, Completion.RETRY, Failure.AUTHENTICATION_FAILURE, now));
        assertTrue(repository.complete(lease, Completion.RETRY, Failure.UNKNOWN_OUTCOME, now));
        assertEquals("PENDING", status(id)); assertTrue(repository.claim(25, now.plusSeconds(29)).isEmpty());
    }

    @Test void claimCommitsOutsideCallerAndCompletionAlsoChecksAttemptFence() {
        Team t = team(); UUID id = row(t, now);
        EmailDeliveryLease lease = new TransactionTemplate(transactions).execute(tx -> {
            var result = repository.claim(1, now).getFirst(); tx.setRollbackOnly(); return result;
        });
        assertEquals("PROCESSING", status(id));
        var wrongAttempt = new EmailDeliveryLease(lease.id(), lease.projectId(), lease.recipientId(), lease.actorId(),
                lease.actorName(), lease.eventType(), lease.eventKey(), lease.taskId(), lease.deliverableId(),
                lease.reviewId(), lease.targetTitle(), lease.createdAt(), lease.claimedAt(), 2);
        assertFalse(repository.complete(wrongAttempt, Completion.SENT, null, now));
        assertTrue(repository.complete(lease, Completion.SENT, null, now));
    }

    private void rejected(org.junit.jupiter.api.function.Executable action) {
        // @Repository의 예외 변환까지 포함한 공개 경계를 검사한다.
        var error = assertThrows(InvalidDataAccessApiUsageException.class, action);
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    private Team team() {
        return new TransactionTemplate(transactions).execute(tx -> {
            User actor = user(em,"합성 요청자"), recipient = user(em,"합성 수신자");
            actor.setEmail(UUID.randomUUID()+"@example.invalid"); recipient.setEmail(UUID.randomUUID()+"@example.invalid");
            Project p = project(em,actor); member(em,p,actor,"LEADER"); member(em,p,recipient,"MEMBER");
            Task task = new Task(); task.setProject(p); task.setCreatedBy(actor); task.setTitle("합성 결과");
            task.setStatus("IN_PROGRESS"); task.setPriority("MEDIUM"); em.persist(task); em.flush();
            syntheticProjects.add(p.getId()); return new Team(p.getId(),actor.getId(),recipient.getId(),task.getId());
        });
    }
    private UUID row(Team team, OffsetDateTime available) {
        UUID id = UUID.randomUUID(); jdbc.update(connection -> { var s=connection.prepareStatement(insertSql()); bind(s,id,team,available); return s; }); return id;
    }
    private String status(UUID id) { return jdbc.queryForObject("SELECT status FROM notification_email_outbox WHERE id=?",String.class,id); }
    private String insertSql() { return """
            INSERT INTO notification_email_outbox(id,project_id,recipient_id,actor_id,actor_name,event_type,event_key,
                task_id,target_title,available_at,created_at,updated_at)
            VALUES(?,?,?,?,'합성 요청자','TASK_COMPLETION_REQUESTED',?,?,'합성 결과',?,?,?)
            """; }
    private void bind(java.sql.PreparedStatement s, UUID id, Team t, OffsetDateTime available) throws java.sql.SQLException {
        s.setObject(1,id); s.setObject(2,t.projectId()); s.setObject(3,t.recipientId()); s.setObject(4,t.actorId());
        s.setString(5,"SYNTHETIC:"+id); s.setObject(6,t.taskId()); s.setObject(7,available); s.setObject(8,now); s.setObject(9,now);
    }
}
