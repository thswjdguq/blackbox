package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ProjectMember {

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

    // LEADER | MEMBER | OBSERVER
    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "joined_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "consent_platform", nullable = false)
    private boolean consentPlatform;

    @Column(name = "consent_github", nullable = false)
    private boolean consentGithub;

    @Column(name = "consent_drive", nullable = false)
    private boolean consentDrive;

    @Column(name = "consent_ai_analysis", nullable = false)
    private boolean consentAiAnalysis;

    @Column(name = "consented_at")
    private OffsetDateTime consentedAt;
}
