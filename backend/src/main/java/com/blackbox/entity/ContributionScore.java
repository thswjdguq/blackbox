package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "contribution_scores",
       uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ContributionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "git_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal gitScore = BigDecimal.ZERO;

    @Column(name = "doc_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal docScore = BigDecimal.ZERO;

    @Column(name = "meeting_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal meetingScore = BigDecimal.ZERO;

    @Column(name = "task_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal taskScore = BigDecimal.ZERO;

    @Column(name = "total_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalScore = BigDecimal.ZERO;

    @Column(name = "weight_git", nullable = false, precision = 3, scale = 2)
    private BigDecimal weightGit = new BigDecimal("0.30");

    @Column(name = "weight_doc", nullable = false, precision = 3, scale = 2)
    private BigDecimal weightDoc = new BigDecimal("0.25");

    @Column(name = "weight_meeting", nullable = false, precision = 3, scale = 2)
    private BigDecimal weightMeeting = new BigDecimal("0.20");

    @Column(name = "weight_task", nullable = false, precision = 3, scale = 2)
    private BigDecimal weightTask = new BigDecimal("0.25");

    // Java에서 직접 관리 (재계산 시 갱신)
    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;
}
