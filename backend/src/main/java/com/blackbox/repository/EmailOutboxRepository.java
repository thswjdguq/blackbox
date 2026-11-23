package com.blackbox.repository;

import com.blackbox.entity.UserNotification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** V39의 발송 대기 INSERT. 업무·앱 알림의 기존 트랜잭션 밖에서 독립 예약하지 않는다. */
@Repository
public class EmailOutboxRepository {
    private final JdbcTemplate jdbc;

    public EmailOutboxRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public int insertIgnoringDuplicate(UserNotification snapshot) {
        return jdbc.update("""
                INSERT INTO notification_email_outbox(project_id, recipient_id, actor_id, actor_name,
                    event_type, event_key, task_id, deliverable_id, review_id, target_title,
                    available_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_key, recipient_id) DO NOTHING
                """, snapshot.getProjectId(), snapshot.getRecipientId(), snapshot.getActorId(), snapshot.getActorName(),
                snapshot.getType(), snapshot.getEventKey(), snapshot.getTaskId(), snapshot.getDeliverableId(),
                snapshot.getReviewId(), snapshot.getTaskTitle(), snapshot.getCreatedAt(), snapshot.getCreatedAt(), snapshot.getCreatedAt());
    }
}
