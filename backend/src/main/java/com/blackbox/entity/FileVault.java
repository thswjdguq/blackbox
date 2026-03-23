package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_vault")
@Getter
@Setter
@NoArgsConstructor
public class FileVault {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    // SHA-256 hash for tamper detection
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "is_flagged", nullable = false)
    private boolean isFlagged = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
