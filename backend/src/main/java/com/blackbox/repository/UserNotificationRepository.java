package com.blackbox.repository;

import com.blackbox.entity.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {

    /** 같은 이벤트의 재처리·동시 요청은 고유 제약에서 조용히 무시한다. 예외로 업무 트랜잭션을 깨지 않는다. */
    @Modifying
    @Query(value = """
            INSERT INTO user_notifications(project_id, recipient_id, actor_id, actor_name, type, event_key,
                task_id, task_title, message_id, file_id, file_name, file_version, created_at)
            VALUES (:projectId, :recipientId, :actorId, :actorName, :type, :eventKey,
                :taskId, :taskTitle, :messageId, :fileId, :fileName, :fileVersion, :createdAt)
            ON CONFLICT (event_key, recipient_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoringDuplicate(@Param("projectId") UUID projectId, @Param("recipientId") UUID recipientId,
                                @Param("actorId") UUID actorId, @Param("actorName") String actorName,
                                @Param("type") String type, @Param("eventKey") String eventKey,
                                @Param("taskId") UUID taskId, @Param("taskTitle") String taskTitle,
                                @Param("messageId") UUID messageId, @Param("fileId") UUID fileId,
                                @Param("fileName") String fileName, @Param("fileVersion") Integer fileVersion,
                                @Param("createdAt") OffsetDateTime createdAt);

    @Query(value = """
            SELECT * FROM user_notifications
            WHERE recipient_id = :recipientId AND project_id = :projectId
              AND (:unreadOnly = FALSE OR read_at IS NULL)
            ORDER BY created_at DESC, id DESC LIMIT :limit
            """, nativeQuery = true)
    List<UserNotification> firstPage(@Param("recipientId") UUID recipientId, @Param("projectId") UUID projectId,
                                     @Param("unreadOnly") boolean unreadOnly, @Param("limit") int limit);

    // 같은 이벤트의 여러 알림은 created_at이 같으므로 id까지 비교해야 페이지 경계에서 빠지거나 겹치지 않는다.
    @Query(value = """
            SELECT * FROM user_notifications
            WHERE recipient_id = :recipientId AND project_id = :projectId
              AND (:unreadOnly = FALSE OR read_at IS NULL)
              AND (created_at < :cursorAt OR (created_at = :cursorAt AND id < :cursorId))
            ORDER BY created_at DESC, id DESC LIMIT :limit
            """, nativeQuery = true)
    List<UserNotification> pageAfter(@Param("recipientId") UUID recipientId, @Param("projectId") UUID projectId,
                                     @Param("unreadOnly") boolean unreadOnly, @Param("cursorAt") OffsetDateTime cursorAt,
                                     @Param("cursorId") UUID cursorId, @Param("limit") int limit);

    /** 동시에 두 번 읽음 요청이 와도 처음 시각만 남도록 비어 있을 때만 채운다. 이후 재조회가 갱신값을 보도록 영속성 컨텍스트를 비운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE user_notifications SET read_at = :readAt WHERE id = :id AND read_at IS NULL", nativeQuery = true)
    int markReadIfUnread(@Param("id") UUID id, @Param("readAt") OffsetDateTime readAt);

    /** 배정 시각(μs). DB 기본값(NOW())으로 저장되므로 엔티티가 아니라 테이블에서 읽는다. 드라이버별 시각 타입 차이를 피해 정수로 받는다. */
    @Query(value = "SELECT CAST(EXTRACT(EPOCH FROM assigned_at) * 1000000 AS BIGINT) FROM task_assignees WHERE task_id = :taskId AND user_id = :userId",
            nativeQuery = true)
    Optional<Long> assignedAtMicros(@Param("taskId") UUID taskId, @Param("userId") UUID userId);

    long countByRecipientIdAndProjectIdAndReadAtIsNull(UUID recipientId, UUID projectId);

    Optional<UserNotification> findByIdAndRecipientIdAndProjectId(UUID id, UUID recipientId, UUID projectId);

    @Query(value = "SELECT id FROM tasks WHERE project_id = :projectId AND id IN (:taskIds)", nativeQuery = true)
    List<UUID> existingTaskIds(@Param("projectId") UUID projectId, @Param("taskIds") Collection<UUID> taskIds);
}
