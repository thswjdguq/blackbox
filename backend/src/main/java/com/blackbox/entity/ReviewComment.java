package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/** K-11: 피드백의 해결 기록을 보존하며 업무 완료와 해결을 구별한다. */
@Entity @Table(name = "review_comments") @Getter @Setter
public class ReviewComment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "deliverable_id") private Deliverable deliverable;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "review_id") private ReviewRound review;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id") private User author;
    @Column(nullable = false, length = 2000) private String content;
    // PostgreSQL timestamptz는 마이크로초 정밀도다. 저장 직후와 재조회 응답의 시각을 같게 한다.
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resolved_by") private User resolvedBy;
    @Column(name = "resolved_at") private OffsetDateTime resolvedAt;
    @Column(name = "resolution_reason", length = 1000) private String resolutionReason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "linked_task_id") private Task linkedTask;

    public String status() {
        if (resolvedAt != null) return "RESOLVED";
        return linkedTask != null && "DONE".equals(linkedTask.getStatus()) ? "REFLECTION_PENDING" : "OPEN";
    }
}
