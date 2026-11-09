package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "task_files") @Getter @Setter
public class TaskFile {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_id", nullable = false) private Task task;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "file_id", nullable = false) private FileVault file;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "linked_by", nullable = false) private User linkedBy;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
}
