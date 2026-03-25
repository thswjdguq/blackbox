package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog {

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

    // PLATFORM | GITHUB | GOOGLE_DRIVE | MANUAL
    @Column(nullable = false, length = 20)
    private String source;

    // TASK_CREATE | TASK_COMPLETE | MEETING_ATTEND | CHECKIN | ...
    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "trust_level", nullable = false, precision = 3, scale = 2)
    private BigDecimal trustLevel;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "synced_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime syncedAt;

    @Column(name = "quality_score", precision = 3, scale = 2)
    private BigDecimal qualityScore;

    @Column(name = "quality_reason", columnDefinition = "TEXT")
    private String qualityReason;

    @Column(name = "analysis_method", length = 20)
    private String analysisMethod;
}
