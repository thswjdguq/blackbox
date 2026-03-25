package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tamper_detection_log")
@Getter
@Setter
@NoArgsConstructor
public class TamperDetectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vault_id", nullable = false)
    private FileVault vault;

    @Column(name = "detected_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "original_hash", nullable = false, length = 64)
    private String originalHash;

    @Column(name = "new_hash", nullable = false, length = 64)
    private String newHash;

    // REUPLOAD | SCHEDULED_CHECK
    @Column(name = "detector_type", nullable = false, length = 20)
    private String detectorType;

    // FLAGGED | REVIEWED | DISMISSED
    @Column(nullable = false, length = 20)
    private String status = "FLAGGED";
}
