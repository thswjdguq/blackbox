package com.blackbox.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** K-41/V39의 짧은 DB 구간만 담당한다. 메서드 반환 뒤 SMTP 중에는 행 잠금을 유지하지 않는다. */
@Repository
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class EmailDeliveryRepository {
    public static final int MAX_BATCH = 25;
    public static final int MAX_ATTEMPTS = 3;
    public static final Duration LEASE = Duration.ofMinutes(2);

    public enum Completion { SENT, RETRY, FAILED, SKIPPED }
    public enum Failure { TEMPORARY_FAILURE, UNKNOWN_OUTCOME, AUTHENTICATION_FAILURE, PERMANENT_FAILURE, CONDITION_CHANGED }

    private final JdbcTemplate jdbc;
    public EmailDeliveryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 만료된 마지막 시도도 같은 상한 안에서 FAILED로 끝낸다. 반환값은 새로 획득한 행뿐이다. */
    public List<EmailDeliveryLease> claim(int limit, OffsetDateTime now) {
        if (limit < 1 || limit > MAX_BATCH) throw new IllegalArgumentException("발송 대기는 1~25건씩 획득하세요.");
        Objects.requireNonNull(now);
        return jdbc.query("""
                WITH due AS (
                    SELECT id FROM notification_email_outbox
                    WHERE (status = 'PENDING' AND available_at <= ?)
                       OR (status = 'PROCESSING' AND claimed_at <= ?)
                    ORDER BY available_at, created_at, id
                    FOR UPDATE SKIP LOCKED LIMIT ?
                )
                UPDATE notification_email_outbox o SET
                    status = CASE WHEN attempt_count >= 3 THEN 'FAILED' ELSE 'PROCESSING' END,
                    claimed_at = CASE WHEN attempt_count >= 3 THEN claimed_at ELSE ? END,
                    attempt_count = CASE WHEN attempt_count >= 3 THEN attempt_count ELSE attempt_count + 1 END,
                    error_code = CASE WHEN attempt_count >= 3 THEN 'MAX_ATTEMPTS' ELSE NULL END
                FROM due WHERE o.id = due.id
                RETURNING o.*
                """, (rs, row) -> "PROCESSING".equals(rs.getString("status")) ? lease(rs) : null,
                now, now.minus(LEASE), limit, now).stream().filter(Objects::nonNull).toList();
    }

    /** 호출자는 SMTP/조건 재확인 결과를 분류해서 전달한다. 원본 예외나 주소는 저장하지 않는다. */
    public boolean complete(EmailDeliveryLease lease, Completion completion, Failure failure, OffsetDateTime now) {
        Objects.requireNonNull(lease); Objects.requireNonNull(completion); Objects.requireNonNull(now);
        boolean retryable = failure == Failure.TEMPORARY_FAILURE || failure == Failure.UNKNOWN_OUTCOME;
        if ((completion == Completion.SENT && failure != null)
                || (completion == Completion.RETRY && !retryable)
                || (completion == Completion.FAILED && failure != Failure.AUTHENTICATION_FAILURE && failure != Failure.PERMANENT_FAILURE)
                || (completion == Completion.SKIPPED && failure != Failure.CONDITION_CHANGED)) {
            throw new IllegalArgumentException("발송 결과와 분류 코드가 일치하지 않습니다.");
        }
        String status = switch (completion) {
            case SENT -> "SENT";
            case SKIPPED -> "SKIPPED";
            case FAILED -> "FAILED";
            case RETRY -> lease.attemptCount() < MAX_ATTEMPTS ? "PENDING" : "FAILED";
        };
        return jdbc.update("""
                UPDATE notification_email_outbox SET status = ?, sent_at = ?, error_code = ?,
                    available_at = CASE WHEN ? = 'PENDING' THEN ? ELSE available_at END
                WHERE id = ? AND status = 'PROCESSING' AND claimed_at = ? AND attempt_count = ?
                    AND claimed_at > ? AND claimed_at <= ?
                """, status, completion == Completion.SENT ? now : null, failure == null ? null : failure.name(),
                status, now.plusSeconds(30L * lease.attemptCount()), lease.id(), lease.claimedAt(), lease.attemptCount(),
                now.minus(LEASE), now) == 1;
    }

    private EmailDeliveryLease lease(ResultSet r) throws SQLException {
        return new EmailDeliveryLease(r.getObject("id", UUID.class), r.getObject("project_id", UUID.class),
                r.getObject("recipient_id", UUID.class), r.getObject("actor_id", UUID.class), r.getString("actor_name"),
                r.getString("event_type"), r.getString("event_key"), r.getObject("task_id", UUID.class),
                r.getObject("deliverable_id", UUID.class), r.getObject("review_id", UUID.class), r.getString("target_title"),
                r.getObject("created_at", OffsetDateTime.class), r.getObject("claimed_at", OffsetDateTime.class), r.getInt("attempt_count"));
    }
}
