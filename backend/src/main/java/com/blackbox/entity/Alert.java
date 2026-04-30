package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // NULL이면 팀 전체 경보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // FREE_RIDE | DROPOUT | OVERLOAD | TAMPER | CRUNCH_TIME | GAMING_SUSPECT
    @Column(name = "alert_type", nullable = false, length = 30)
    private String alertType;

    // LOW | MEDIUM | HIGH | CRITICAL
    @Column(nullable = false, length = 10)
    private String severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolve_reason", length = 200)
    private String resolveReason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean isResolved() {
        return resolvedAt != null;
    }
}
