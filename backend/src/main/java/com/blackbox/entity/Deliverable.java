package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "deliverables") @Getter @Setter
public class Deliverable {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "submission_method", length = 500) private String submissionMethod;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_id") private User owner;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
}
