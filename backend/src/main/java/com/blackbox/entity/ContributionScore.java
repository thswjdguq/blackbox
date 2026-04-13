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

    // ── 기존 숫자 점수 컬럼 (하위 호환 유지) ──────────────────────────────
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

    // ── 참여 여부 필드 (V11) ──────────────────────────────────────────────
    /** 담당 태스크 중 1개 이상 DONE */
    @Column(name = "task_participated", nullable = false)
    private boolean taskParticipated = false;

    /** 프로젝트 회의 중 50% 이상 체크인 */
    @Column(name = "meeting_participated", nullable = false)
    private boolean meetingParticipated = false;

    /** 파일 1개 이상 업로드 */
    @Column(name = "file_participated", nullable = false)
    private boolean fileParticipated = false;

    /** 담당 액션아이템 중 1개 이상 DONE */
    @Column(name = "action_participated", nullable = false)
    private boolean actionParticipated = false;

    /** FULL (4개 참) / PARTIAL (2~3개 참) / NONE (0~1개 참) */
    @Column(name = "participation_level", nullable = false, length = 10)
    private String participationLevel = "NONE";

    // Java에서 직접 관리 (재계산 시 갱신)
    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;
}
