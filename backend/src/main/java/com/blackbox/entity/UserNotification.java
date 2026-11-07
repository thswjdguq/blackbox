package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 받는 사람 한 명의 개인 알림(K-54). 생성은 NotificationService의 중복 무시 INSERT로만 한다. */
@Entity
@Table(name = "user_notifications")
@Getter
@Setter
@NoArgsConstructor
public class UserNotification {
    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;
    @Column(name = "actor_name", nullable = false, length = 100)
    private String actorName;
    @Column(nullable = false, length = 40)
    private String type;
    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;
    @Column(name = "task_id")
    private UUID taskId;
    @Column(name = "deliverable_id") private UUID deliverableId;
    @Column(name = "review_id") private UUID reviewId;
    @Column(name = "comment_id") private UUID commentId;
    @Column(name = "task_title", nullable = false)
    private String taskTitle;
    @Column(name = "message_id")
    private UUID messageId;
    @Column(name = "file_id")
    private UUID fileId;
    @Column(name = "file_name")
    private String fileName;
    @Column(name = "file_version")
    private Integer fileVersion;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "read_at")
    private OffsetDateTime readAt;
}
