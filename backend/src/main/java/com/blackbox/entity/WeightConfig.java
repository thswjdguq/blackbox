package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "weight_configs")
@Getter
@Setter
@NoArgsConstructor
public class WeightConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id", nullable = false)
    private User professor;

    @Column(name = "weight_git", nullable = false, precision = 3, scale = 2)
    private BigDecimal weightGit = new BigDecimal("0.30");

    @Column(name = "weight_doc", nullable = false, precision = 3, scale = 2)
    private BigDecimal weightDoc = new BigDecimal("0.25");

    @Column(name = "weight_meeting", nullable = false, precision = 3, scale = 2)
    private BigDecimal weightMeeting = new BigDecimal("0.20");

    @Column(name = "weight_task", nullable = false, precision = 3, scale = 2)
    private BigDecimal weightTask = new BigDecimal("0.25");

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
