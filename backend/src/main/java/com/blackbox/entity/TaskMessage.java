package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "task_messages") @Getter @Setter
public class TaskMessage {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_id", nullable = false) private Task task;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id", nullable = false) private User author;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipient_id") private User recipient;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_id") private TaskMessage parent;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "file_id") private FileVault file;
    @Column(nullable = false, length = 2000) private String body;
    @Column(nullable = false, length = 16) private String kind;
    @Column(name = "answered_at") private OffsetDateTime answeredAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
}
