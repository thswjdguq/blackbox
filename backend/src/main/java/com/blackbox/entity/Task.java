package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deliverable_id")
    private Deliverable deliverable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id")
    private DeliverableRequirement requirement;

    @Column(name = "completion_criteria", length = 2000)
    private String completionCriteria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "predecessor_id")
    private Task predecessor;

    @Column(name = "attachment_required", nullable = false)
    private boolean attachmentRequired;

    @Column(name = "completion_requested_at")
    private OffsetDateTime completionRequestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completion_requested_by")
    private User completionRequestedBy;

    @Column(name = "completion_approved_at")
    private OffsetDateTime completionApprovedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completion_approved_by")
    private User completionApprovedBy;

    @Column(name = "completion_rejection_reason", length = 2000)
    private String completionRejectionReason;

    // TODO | IN_PROGRESS | DONE
    @Column(nullable = false, length = 20)
    private String status;

    // LOW | MEDIUM | HIGH | URGENT
    @Column(nullable = false, length = 10)
    private String priority;

    @Column(length = 30)
    private String tag;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
